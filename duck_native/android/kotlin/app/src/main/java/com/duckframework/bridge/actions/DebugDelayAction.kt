package com.duckframework.bridge.actions

import android.os.Handler
import android.os.Looper
import com.duckframework.bridge.NativeAction
import com.duckframework.bridge.NativeCallback
import com.duckframework.bridge.NativeHost
import com.duckframework.bridge.Payload
import org.json.JSONObject

/**
 * Debug-only action that completes after a delay.
 *
 * It exists to test asynchronous completion and out-of-order responses without
 * needing a real picker or permission dialog. Payload:
 * `{ "ms": number, "label": string }`, both optional. Result echoes them.
 */
class DebugDelayAction : NativeAction {

    private val handler = Handler(Looper.getMainLooper())

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        // Validate the payload
        val delay =
            Payload.optionalLong(payload, "ms", DEFAULT_DELAY_MS, 0L, MAX_DELAY_MS)
        val label = Payload.optionalString(payload, "label", MAX_LABEL_LENGTH) ?: ""

        // Return now and complete later, like a real picker would
        handler.postDelayed(
            {
                callback.success(
                    JSONObject().put("waited", delay).put("label", label),
                )
            },
            delay,
        )
    }

    companion object {
        const val DEFAULT_DELAY_MS = 1000L
        const val MAX_DELAY_MS = 10_000L
        const val MAX_LABEL_LENGTH = 64
    }
}
