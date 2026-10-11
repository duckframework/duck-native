package com.duckframework.bridge.actions

import android.widget.Toast
import com.duckframework.bridge.NativeAction
import com.duckframework.bridge.NativeCallback
import com.duckframework.bridge.NativeHost
import com.duckframework.bridge.Payload
import org.json.JSONObject

/**
 * Shows a short Android Toast.
 *
 * Payload: `{ "message": string }`.
 */
class ToastAction : NativeAction {

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        // Validate the message
        val message = Payload.requireString(payload, "message", MAX_MESSAGE_LENGTH)

        // Show it; actions already run on the main thread
        Toast.makeText(
            host.activity,
            message,
            Toast.LENGTH_SHORT,
        ).show()

        callback.success()
    }

    companion object {
        const val MAX_MESSAGE_LENGTH = 500
    }
}
