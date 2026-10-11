package com.duckframework.bridge

import android.util.Log

/**
 * Controlled logging for the bridge.
 *
 * Detailed logs appear only in debuggable builds. Payloads are never logged,
 * because they may contain clipboard text or other private data.
 */
object BridgeLog {

    private const val TAG = "DuckBridge"

    @Volatile
    var enabled: Boolean = false

    /**
     * Logs a development message. Silent in release builds.
     */
    fun debug(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    /**
     * Logs something unexpected that the bridge recovered from.
     */
    fun warn(message: String) {
        Log.w(TAG, message)
    }

    /**
     * Logs a failure. The stack trace is included only in debuggable builds.
     */
    fun error(message: String, cause: Throwable? = null) {
        Log.e(TAG, message, if (enabled) cause else null)
    }
}
