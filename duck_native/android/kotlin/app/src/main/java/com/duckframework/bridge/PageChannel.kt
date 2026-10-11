package com.duckframework.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject

/**
 * The only way native code talks back to the page.
 *
 * Responses and events are delivered on the main thread, and only while the
 * WebView is showing a trusted origin. That stops a result requested by a
 * trusted page from leaking to an untrusted page that navigated in later.
 *
 * Delivery calls `window.DuckNative`, which is injected by `duck-native.js`.
 */
class PageChannel(
    private val webView: WebView,
    private val trust: TrustPolicy,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var closed = false

    /**
     * Sends a successful response for request [id].
     *
     * @param id Request id from JavaScript.
     * @param result Structured result, or null.
     */
    fun respondSuccess(id: String, result: JSONObject?) {
        val response = JSONObject()
            .put("id", id)
            .put("ok", true)
            // JSONObject.put(key, null) would delete the key, so use NULL
            .put("result", result ?: JSONObject.NULL)
        deliver("resolve(${JSONObject.quote(id)},$response)")
    }

    /**
     * Sends a failed response for request [id].
     *
     * @param id Request id from JavaScript.
     * @param code Stable error code.
     * @param message Short, safe description.
     */
    fun respondError(id: String, code: ErrorCode, message: String) {
        val error = JSONObject().put("code", code.name).put("message", message)
        val response = JSONObject().put("id", id).put("ok", false).put("error", error)
        deliver("resolve(${JSONObject.quote(id)},$response)")
    }

    /**
     * Sends a native-initiated event to the page.
     *
     * @param event Event name, for example `resume`.
     * @param payload Event data.
     */
    fun emit(event: String, payload: JSONObject = JSONObject()) {
        deliver("dispatch(${JSONObject.quote(event)},$payload)")
    }

    /**
     * Stops all delivery. Call when the WebView is destroyed.
     */
    fun close() {
        closed = true
    }

    private fun deliver(call: String) {
        mainHandler.post {
            if (closed) return@post

            // Only talk to trusted pages
            if (!trust.isTrusted(webView.url)) {
                BridgeLog.debug("Dropped message: page is not trusted")
                return@post
            }

            val script = "window.DuckNative&&window.DuckNative.$call"
            webView.evaluateJavascript(script, null)
        }
    }
}
