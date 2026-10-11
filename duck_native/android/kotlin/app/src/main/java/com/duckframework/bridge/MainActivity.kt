package com.duckframework.bridge

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONObject

/**
 * Hosts the WebView that runs the Duck/Lively application.
 *
 * The Activity wires things together and owns the connection state: it connects
 * on launch, shows [ConnectView] while loading or after a failure, and replaces
 * the WebView if its renderer dies. Server address, trusted origins and every
 * native capability live in their own classes.
 */
class MainActivity : Activity(), ConnectView.Listener {

    private enum class State { CONNECTING, CONNECTED, ERROR }

    private lateinit var config: BridgeConfig
    private lateinit var trust: TrustPolicy
    private lateinit var store: ServerStore
    private lateinit var root: View
    private lateinit var webContainer: FrameLayout
    private lateinit var connectView: ConnectView
    private lateinit var permissionRequester: PermissionRequester
    private lateinit var networkMonitor: NetworkMonitor

    private val bootstrapScript by lazy { readAsset(BOOTSTRAP_ASSET) }
    private val handler = Handler(Looper.getMainLooper())
    private val crashTimes = ArrayDeque<Long>()

    private var debuggable = false
    private var webView: WebView? = null
    private var channel: PageChannel? = null

    // The server whose pages may use the native API
    private var server = ServerUrl.DEFAULT
    private var state = State.CONNECTING
    private var failedUrl: String? = null
    private var errorMidSession = false

    private val timeout = Runnable {
        if (state == State.CONNECTING) {
            webView?.stopLoading()
            showFailure(LoadFailure.TIMEOUT)
        }
    }

    private val pageListener = object : PageListener {
        override fun onPageLoaded(url: String?) {
            if (state != State.CONNECTING) return
            handler.removeCallbacks(timeout)
            state = State.CONNECTED

            // Remember typed servers, but never the built-in test page
            if (config.canChangeServer && server != BridgePage.URL) {
                store.lastUrl = server
            }
            connectView.hide()
        }

        override fun onLoadFailed(url: String?, failure: LoadFailure) {
            if (state == State.ERROR) return
            errorMidSession = state == State.CONNECTED
            failedUrl = url
            showFailure(failure)
        }

        override fun onRendererGone(crashed: Boolean, url: String?) {
            recoverFromRendererGone(crashed, url)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Resolve runtime configuration
        debuggable = isDebuggable()
        BridgeLog.enabled = debuggable
        config = BridgeConfig.load(this, intent, debuggable)
        store = ServerStore(this)
        server = config.serverUrl ?: store.lastUrl ?: ServerUrl.DEFAULT
        trust = TrustPolicy(trustedUrlsFor(server))

        // Build the screen
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        webContainer = findViewById(R.id.web_container)
        connectView = ConnectView(
            root = findViewById(R.id.connect_root),
            listener = this,
            showTests = debuggable && config.canChangeServer,
        )
        setUpEdgeToEdge()
        applyDisplaySettings()

        // Native helpers that outlive any single WebView
        permissionRequester = PermissionRequester(this)
        networkMonitor = NetworkMonitor(this) { online ->
            channel?.emit("networkChanged", JSONObject().put("online", online))
        }

        // Restore history after process death when possible, otherwise connect
        val view = attachWebView()
        val restored = savedInstanceState?.let { view.restoreState(it) }
        if (restored == null) {
            connect(server)
        } else {
            state = State.CONNECTED
        }
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.start()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()

        // Lets the page reconnect its WebSocket after time in the background
        channel?.emit("resume")
    }

    override fun onPause() {
        channel?.emit("pause")
        webView?.onPause()
        super.onPause()
    }

    override fun onStop() {
        networkMonitor.stop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView?.saveState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        detachWebView()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val view = webView

        // From an error that hit a running session, return to the page underneath
        if (state == State.ERROR && errorMidSession && view != null && view.canGoBack()) {
            state = State.CONNECTED
            connectView.hide()
            view.goBack()
        } else if (state != State.ERROR && view != null && view.canGoBack()) {
            view.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionRequester.onResult(requestCode, grantResults)
    }

    override fun onConnect(input: String) {
        when (val result = ServerUrl.parse(input)) {
            is ServerUrl.Result.Invalid -> connectView.showInputError(result.messageRes)
            is ServerUrl.Result.Valid -> connect(result.url, newServer = result.url)
        }
    }

    override fun onRetry() = connect(failedUrl ?: server)

    override fun onOpenTests() = connect(BridgePage.URL, newServer = BridgePage.URL)

    /**
     * Loads [url] behind the progress screen and starts the timeout.
     *
     * @param url Page to load.
     * @param newServer Server to trust from now on; unchanged by default.
     * @param reloading True when restoring a page after a renderer failure.
     */
    private fun connect(
        url: String,
        newServer: String = server,
        reloading: Boolean = false,
    ) {
        server = newServer
        failedUrl = null
        trust.setTrusted(trustedUrlsFor(server))

        // Show progress and start the clock
        state = State.CONNECTING
        if (reloading) {
            connectView.showConnecting(
                getString(R.string.reloading_title),
                getString(R.string.reloading_message),
            )
        } else {
            connectView.showConnecting(
                getString(R.string.connecting_title),
                getString(R.string.connecting_message, url),
            )
        }
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, CONNECT_TIMEOUT_MS)

        // Load the page, creating a WebView if the last one was discarded
        val view = webView ?: attachWebView()
        view.stopLoading()
        view.loadUrl(url)
    }

    private fun showFailure(failure: LoadFailure) {
        handler.removeCallbacks(timeout)
        state = State.ERROR
        val error = ConnectionErrors.describe(
            context = this,
            failure = failure,
            url = failedUrl ?: server,
            offline = !networkMonitor.isOnline(),
        )
        connectView.showError(error, config.canChangeServer, server)
    }

    private fun recoverFromRendererGone(crashed: Boolean, url: String?) {
        BridgeLog.warn("WebView renderer gone, crashed=$crashed")

        // Resume where the user was, but only on a trusted page
        val resumeUrl = url?.takeIf { trust.isTrusted(it) } ?: server
        handler.removeCallbacks(timeout)
        detachWebView()

        // Forget old crashes so a rare one never counts toward a loop
        val now = SystemClock.elapsedRealtime()
        crashTimes.addLast(now)
        while (crashTimes.isNotEmpty() && now - crashTimes.first() > CRASH_WINDOW_MS) {
            crashTimes.removeFirst()
        }

        // Stop a crash loop from reloading forever
        if (crashTimes.size >= MAX_CRASHES) {
            crashTimes.clear()
            errorMidSession = false
            failedUrl = resumeUrl
            showFailure(LoadFailure.RENDERER)
            return
        }

        // Otherwise reload behind the progress screen; connect() builds a new WebView
        connect(resumeUrl, reloading = true)
    }

    private fun attachWebView(): WebView {
        val view = WebView(this)
        configureWebView(view)

        // Wire the bridge; it is the only object JavaScript can reach
        val newChannel = PageChannel(view, trust)
        val bridge = DuckNativeBridge(
            webView = view,
            host = NativeHost(this, permissionRequester),
            registry = NativeActionRegistry.createDefault(debuggable),
            trust = trust,
            channel = newChannel,
        )
        view.addJavascriptInterface(bridge, BRIDGE_NAME)
        view.webViewClient = DuckWebViewClient(
            activity = this,
            config = config,
            trust = trust,
            bootstrapScript = bootstrapScript,
            listener = pageListener,
        )

        webContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        webView = view
        channel = newChannel
        return view
    }

    private fun detachWebView() {
        channel?.close()
        webView?.let { view ->
            webContainer.removeView(view)
            view.destroy()
        }
        webView = null
        channel = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(view: WebView) {
        with(view.settings) {
            // Required by Lively; DOM storage is scoped per origin
            javaScriptEnabled = true
            domStorageEnabled = true

            // Nothing here needs local files or insecure content
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // Remote debugging through chrome://inspect, for debug builds only
        if (debuggable) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }

    /**
     * Draws behind the system bars and pads for them and the keyboard.
     *
     * Android 15+ forces this layout, so it is handled explicitly on Android 11+.
     * Older versions resize the window for the keyboard (see the manifest).
     */
    @Suppress("DEPRECATION")
    private fun setUpEdgeToEdge() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        window.setDecorFitsSystemWindows(false)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val keyboard = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
    }

    /**
     * Applies the FULLSCREEN and KEEP_SCREEN_ON settings from config.py.
     */
    @Suppress("DEPRECATION")
    private fun applyDisplaySettings() {
        if (BuildConfig.KEEP_SCREEN_ON) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (!BuildConfig.FULLSCREEN) return

        // Hide the system bars; a swipe from the edge shows them briefly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun trustedUrlsFor(server: String): List<String> =
        listOf(server) + config.trustedOrigins

    private fun readAsset(name: String): String =
        assets.open(name).bufferedReader().use { it.readText() }

    private fun isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        const val BRIDGE_NAME = "AndroidDuckBridge"
        const val BOOTSTRAP_ASSET = "duck-native.js"

        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val CRASH_WINDOW_MS = 60_000L
        private const val MAX_CRASHES = 3
    }
}
