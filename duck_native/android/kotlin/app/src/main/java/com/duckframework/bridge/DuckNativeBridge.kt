package com.duckframework.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The single native entry point exposed to JavaScript as `AndroidDuckBridge`.
 *
 * It has exactly one annotated method, [request]. JavaScript cannot reach the
 * Activity, the WebView or any other object through it, and it can only run
 * actions registered in the [NativeActionRegistry].
 *
 * Nothing here is shared between requests: every request carries its own id and
 * its own callback, so many can be in flight and finish in any order.
 */
class DuckNativeBridge(
    private val webView: WebView,
    private val host: NativeHost,
    private val registry: NativeActionRegistry,
    private val trust: TrustPolicy,
    private val channel: PageChannel,
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Receives one JSON request from JavaScript.
     *
     * Called on a WebView background thread, so it only parses and validates
     * the envelope here and then moves to the main thread.
     *
     * @param requestJson `{ "id": string, "action": string, "payload": object }`.
     */
    @JavascriptInterface
    fun request(requestJson: String?) {
        // Reject missing or oversized input
        if (requestJson == null || requestJson.length > MAX_REQUEST_CHARS) {
            BridgeLog.warn("Dropped request: missing or too large")
            return
        }

        // Parse the envelope
        val envelope = try {
            JSONObject(requestJson)
        } catch (error: JSONException) {
            BridgeLog.warn("Dropped request: malformed JSON")
            return
        }

        // Without a valid id there is nobody to answer
        val id = envelope.opt("id") as? String
        if (id.isNullOrEmpty() || id.length > MAX_ID_LENGTH) {
            BridgeLog.warn("Dropped request: invalid id")
            return
        }

        // Validate the action name and payload shape
        val action = envelope.opt("action") as? String
        if (action.isNullOrEmpty()) {
            channel.respondError(id, ErrorCode.INVALID_REQUEST, "Missing action")
            return
        }
        val payload = when (val raw = envelope.opt("payload")) {
            null, JSONObject.NULL -> JSONObject()
            is JSONObject -> raw
            else -> {
                channel.respondError(
                    id,
                    ErrorCode.INVALID_PAYLOAD,
                    "Payload must be an object",
                )
                return
            }
        }

        // Hand off to the main thread
        mainHandler.post { dispatch(id, action, payload) }
    }

    private fun dispatch(id: String, actionName: String, payload: JSONObject) {
        // Enforce the trust boundary before any action code runs
        if (!trust.isTrusted(webView.url)) {
            BridgeLog.warn("Rejected id=$id: page is not trusted")
            return
        }
        BridgeLog.debug("received action=$actionName id=$id")

        // Look up the action
        val action = registry.find(actionName)
        if (action == null) {
            channel.respondError(id, ErrorCode.UNKNOWN_ACTION, "Unknown native action")
            return
        }

        // Run it, turning any failure into a structured error
        val callback = RequestCallback(id, actionName)
        try {
            action.execute(host, payload, callback)
        } catch (error: NativeException) {
            callback.error(error.code, error.message.orEmpty())
        } catch (error: Exception) {
            BridgeLog.error("action=$actionName id=$id failed", error)
            callback.error(ErrorCode.NATIVE_ERROR, "Native operation failed")
        }
    }

    /**
     * Completes one request, at most once, whichever thread calls it.
     */
    private inner class RequestCallback(
        private val id: String,
        private val actionName: String,
    ) : NativeCallback {

        private val completed = AtomicBoolean(false)

        override fun success(result: JSONObject?) {
            if (!completed.compareAndSet(false, true)) return
            BridgeLog.debug("completed action=$actionName id=$id")
            channel.respondSuccess(id, result)
        }

        override fun error(code: ErrorCode, message: String) {
            if (!completed.compareAndSet(false, true)) return
            BridgeLog.debug("failed action=$actionName id=$id code=${code.name}")
            channel.respondError(id, code, message)
        }
    }

    companion object {
        const val MAX_REQUEST_CHARS = 262_144
        const val MAX_ID_LENGTH = 64
    }
}
