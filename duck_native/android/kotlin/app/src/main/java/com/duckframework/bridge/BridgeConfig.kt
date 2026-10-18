package com.duckframework.bridge

import android.content.Context
import android.content.Intent
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * What to do with http(s) links that leave the trusted origins.
 */
enum class ExternalLinkPolicy {
    /** Open them in the system browser. */
    BROWSER,

    /** Load them inside the WebView, without native API access. */
    WEBVIEW,

    /** Ignore them. */
    BLOCK,
}

/**
 * Runtime configuration for the bridge.
 *
 * Nothing here is hard-coded to a particular Duck server. Values come from
 * `assets/duck-bridge.json`, falling back to the SERVER_URL set at build time.
 * Debuggable builds also accept a `server_url` intent extra so a server can be
 * changed without rebuilding.
 *
 * @property serverUrl Duck server to load, or null to let the user choose one.
 * @property trustedOrigins Extra origins allowed to use the native API.
 * @property externalLinks Policy for links outside the trusted origins.
 */
data class BridgeConfig(
    val serverUrl: String?,
    val trustedOrigins: List<String>,
    val externalLinks: ExternalLinkPolicy,
) {

    /**
     * True when no server is pinned, so the user may enter one on the connect screen.
     */
    val canChangeServer: Boolean
        get() = serverUrl == null

    companion object {
        const val EXTRA_SERVER_URL = "server_url"

        private const val ASSET_NAME = "duck-bridge.json"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TRUSTED_ORIGINS = "trusted_origins"
        private const val KEY_EXTERNAL_LINKS = "external_links"

        /**
         * Loads the configuration.
         *
         * @param context Used to read the bundled config asset.
         * @param intent Launch intent, consulted only in debuggable builds.
         * @param debuggable Whether this is a debuggable build.
         */
        fun load(context: Context, intent: Intent?, debuggable: Boolean): BridgeConfig {
            val json = readAsset(context)

            // Server URL from the asset or the build, optionally overridden while developing
            var serverUrl = validUrl(json.stringOrNull(KEY_SERVER_URL))
                ?: validUrl(BuildConfig.SERVER_URL.ifEmpty { null })
            if (debuggable) {
                val debugUrl = validUrl(intent?.getStringExtra(EXTRA_SERVER_URL))
                serverUrl = debugUrl ?: serverUrl
            }

            // Extra trusted origins
            val trusted = mutableListOf<String>()
            val array = json.optJSONArray(KEY_TRUSTED_ORIGINS)
            for (index in 0 until (array?.length() ?: 0)) {
                validUrl(array?.opt(index) as? String)?.let { trusted.add(it) }
            }

            return BridgeConfig(
                serverUrl = serverUrl,
                trustedOrigins = trusted,
                externalLinks = parsePolicy(json.stringOrNull(KEY_EXTERNAL_LINKS)),
            )
        }

        private fun readAsset(context: Context): JSONObject {
            return try {
                val text = context.assets.open(ASSET_NAME)
                    .bufferedReader()
                    .use { it.readText() }
                JSONObject(text)
            } catch (error: IOException) {
                BridgeLog.warn("No $ASSET_NAME found, using defaults")
                JSONObject()
            } catch (error: JSONException) {
                BridgeLog.error("$ASSET_NAME is not valid JSON, using defaults")
                JSONObject()
            }
        }

        private fun validUrl(url: String?): String? {
            if (url == null) return null
            if (TrustPolicy.originOf(url) != null) return url
            BridgeLog.error("Ignoring invalid URL in configuration")
            return null
        }

        private fun parsePolicy(value: String?): ExternalLinkPolicy {
            if (value == null) return ExternalLinkPolicy.BROWSER
            return try {
                ExternalLinkPolicy.valueOf(value.uppercase(Locale.ROOT))
            } catch (error: IllegalArgumentException) {
                BridgeLog.error("Unknown external_links value, using browser")
                ExternalLinkPolicy.BROWSER
            }
        }

        private fun JSONObject.stringOrNull(key: String): String? = opt(key) as? String
    }
}
