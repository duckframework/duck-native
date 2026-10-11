package com.duckframework.bridge.actions

import android.os.Build
import com.duckframework.bridge.NativeAction
import com.duckframework.bridge.NativeCallback
import com.duckframework.bridge.NativeHost
import org.json.JSONObject

/**
 * Returns coarse, non-identifying device information.
 *
 * Result: `{ "platform", "version", "sdk", "architecture" }`. There are no
 * persistent identifiers here on purpose.
 */
class DeviceInfoAction : NativeAction {

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        callback.success(
            JSONObject()
                .put("platform", PLATFORM)
                .put("version", Build.VERSION.RELEASE)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: UNKNOWN),
        )
    }

    companion object {
        const val PLATFORM = "android"
        const val UNKNOWN = "unknown"
    }
}
