package com.duckframework.bridge

import android.content.Context
import android.webkit.WebViewClient

/**
 * A failed page load, from the WebView or raised by the app itself.
 *
 * @property code A [WebViewClient] error code, or [RENDERER_GONE].
 * @property description Technical detail such as `net::ERR_CONNECTION_REFUSED`.
 */
data class LoadFailure(val code: Int, val description: String?) {

    companion object {
        const val RENDERER_GONE = -1000

        val TIMEOUT = LoadFailure(WebViewClient.ERROR_TIMEOUT, null)
        val SSL = LoadFailure(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, "SSL error")
        val RENDERER = LoadFailure(RENDERER_GONE, null)
    }
}

/**
 * What the error screen shows.
 *
 * @property title Short headline.
 * @property message Plain explanation and what to try.
 * @property detail Small technical line for support, or null.
 */
data class ConnectionError(val title: String, val message: String, val detail: String?)

/**
 * Turns load failures into readable messages.
 */
object ConnectionErrors {

    /**
     * Describes [failure] for the person using the app.
     *
     * @param context Used to read string resources.
     * @param failure What went wrong.
     * @param url The address that failed.
     * @param offline Whether the device has no validated internet access.
     */
    fun describe(
        context: Context,
        failure: LoadFailure,
        url: String?,
        offline: Boolean,
    ): ConnectionError {
        // A local server still works without internet, so offline only matters elsewhere
        val local = ServerUrl.isLocal(url)

        // Pick wording for this kind of failure
        val (titleRes, messageRes) = when {
            failure.code == LoadFailure.RENDERER_GONE ->
                R.string.error_renderer_title to R.string.error_renderer_message
            offline && !local ->
                R.string.error_offline_title to R.string.error_offline_message
            else -> when (failure.code) {
                WebViewClient.ERROR_HOST_LOOKUP ->
                    R.string.error_dns_title to R.string.error_dns_message
                WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_IO ->
                    R.string.error_connect_title to R.string.error_connect_message
                WebViewClient.ERROR_TIMEOUT ->
                    R.string.error_timeout_title to R.string.error_timeout_message
                WebViewClient.ERROR_FAILED_SSL_HANDSHAKE ->
                    R.string.error_ssl_title to R.string.error_ssl_message
                else ->
                    R.string.error_generic_title to R.string.error_generic_message
            }
        }

        // Servers on a computer need their port forwarded to the phone
        var message = context.getString(messageRes)
        val port = ServerUrl.adbPort(url)
        if (failure.code == WebViewClient.ERROR_CONNECT && port != null) {
            message += "\n\n" + context.getString(R.string.error_connect_local_hint, port)
        }

        return ConnectionError(
            title = context.getString(titleRes),
            message = message,
            detail = failure.description?.takeIf { it.isNotBlank() },
        )
    }
}
