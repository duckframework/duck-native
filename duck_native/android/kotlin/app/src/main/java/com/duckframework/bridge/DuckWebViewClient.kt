package com.duckframework.bridge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Receives main-frame load results and renderer failures from the WebView.
 */
interface PageListener {

    /** The main frame finished loading without an error. */
    fun onPageLoaded(url: String?)

    /** The main frame failed to load. Called at most once per navigation. */
    fun onLoadFailed(url: String?, failure: LoadFailure)

    /** The WebView's renderer died; the WebView must be replaced. */
    fun onRendererGone(crashed: Boolean, url: String?)
}

/**
 * Navigation policy, bootstrap injection, load reporting and the test page.
 *
 * Navigation rules for the main frame:
 * - trusted origins load in the WebView;
 * - other http(s) links follow [ExternalLinkPolicy];
 * - `tel:` and `mailto:` open the dialer and mail app;
 * - everything else (`intent:`, `file:`, `content:`, `javascript:`...) is blocked.
 *
 * Subframes are not restricted here. See the README for what that means.
 *
 * @param activity Used to start external apps.
 * @param config Runtime configuration.
 * @param trust Origin trust decisions.
 * @param bootstrapScript Contents of `duck-native.js`.
 * @param listener Told how each main-frame load ended.
 */
class DuckWebViewClient(
    private val activity: Activity,
    private val config: BridgeConfig,
    private val trust: TrustPolicy,
    private val bootstrapScript: String,
    private val listener: PageListener,
) : WebViewClient() {

    private var pageUrl: String? = null
    private var mainFrameFailed = false

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        pageUrl = url
        mainFrameFailed = false
        injectBootstrap(view, url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)

        // Second chance in case the first injection ran before the document existed
        injectBootstrap(view, url)

        // The error page also "finishes", so only report clean loads
        if (!mainFrameFailed) listener.onPageLoaded(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        super.onReceivedError(view, request, error)
        if (!request.isForMainFrame) return

        // A navigation replaced by a newer one is not a failure
        val description = error.description?.toString()
        if (description?.contains(ABORTED) == true) return

        mainFrameFailed = true
        listener.onLoadFailed(request.url.toString(), LoadFailure(error.errorCode, description))
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // Never proceed past a certificate problem
        handler.cancel()

        // Subresource errors are cancelled quietly; only the page itself is reported
        if (error.url != pageUrl) return
        mainFrameFailed = true
        listener.onLoadFailed(error.url, LoadFailure.SSL)
    }

    override fun onRenderProcessGone(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ): Boolean {
        // Returning true keeps the app alive; the Activity replaces the WebView
        listener.onRendererGone(detail.didCrash(), pageUrl)
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        // Serve the test page from memory when no server is pinned
        val isTestPage = config.canChangeServer &&
            request.method == "GET" &&
            request.url.toString() == BridgePage.URL
        if (!isTestPage) return null

        return WebResourceResponse(
            "text/html",
            "UTF-8",
            ByteArrayInputStream(BridgePage.html.toByteArray()),
        )
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = handleNavigation(request.url.toString(), request.isForMainFrame)

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
        handleNavigation(url, isMainFrame = true)

    private fun injectBootstrap(view: WebView, url: String?) {
        // Untrusted pages never get a DuckNative object
        if (trust.isTrusted(url)) {
            view.evaluateJavascript(bootstrapScript, null)
        }
    }

    private fun handleNavigation(url: String, isMainFrame: Boolean): Boolean {
        if (!isMainFrame) return false

        return when (TrustPolicy.schemeOf(url)) {
            "http", "https" -> handleWebLink(url)
            "tel" -> openExternal(Intent(Intent.ACTION_DIAL, Uri.parse(url)))
            "mailto" -> openExternal(Intent(Intent.ACTION_SENDTO, Uri.parse(url)))
            else -> {
                BridgeLog.warn("Blocked navigation to a disallowed scheme")
                true
            }
        }
    }

    private fun handleWebLink(url: String): Boolean {
        if (trust.isTrusted(url)) return false

        return when (config.externalLinks) {
            ExternalLinkPolicy.WEBVIEW -> false
            ExternalLinkPolicy.BLOCK -> true
            ExternalLinkPolicy.BROWSER -> {
                val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                openExternal(view)
            }
        }
    }

    /**
     * Starts an external app and always returns true to cancel the navigation.
     */
    private fun openExternal(intent: Intent): Boolean {
        try {
            activity.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            BridgeLog.warn("No app can open this link")
        }
        return true
    }

    private companion object {
        const val ABORTED = "ERR_ABORTED"
    }
}
