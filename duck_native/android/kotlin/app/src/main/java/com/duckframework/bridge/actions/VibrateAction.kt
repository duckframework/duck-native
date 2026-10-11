package com.duckframework.bridge.actions

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.duckframework.bridge.ErrorCode
import com.duckframework.bridge.NativeAction
import com.duckframework.bridge.NativeCallback
import com.duckframework.bridge.NativeHost
import com.duckframework.bridge.Payload
import org.json.JSONObject

/**
 * Vibrates the device once.
 *
 * Payload: `{ "duration": milliseconds }`, optional, 1 to 5000, default 100.
 */
class VibrateAction : NativeAction {

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        // Validate the duration
        val duration = Payload.optionalLong(
            payload,
            "duration",
            DEFAULT_DURATION_MS,
            MIN_DURATION_MS,
            MAX_DURATION_MS,
        )

        // Make sure the device can vibrate
        val vibrator = vibratorFor(host.activity)
        if (!vibrator.hasVibrator()) {
            callback.error(ErrorCode.NOT_SUPPORTED, "This device cannot vibrate")
            return
        }

        // Vibrate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }

        callback.success()
    }

    @Suppress("DEPRECATION")
    private fun vibratorFor(context: Context): Vibrator {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.getSystemService(VibratorManager::class.java).defaultVibrator
        }
        return context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    companion object {
        const val DEFAULT_DURATION_MS = 100L
        const val MIN_DURATION_MS = 1L
        const val MAX_DURATION_MS = 5000L
    }
}
