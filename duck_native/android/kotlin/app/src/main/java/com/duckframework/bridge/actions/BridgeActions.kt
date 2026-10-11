package com.duckframework.bridge.actions

import com.duckframework.bridge.NativeAction
import com.duckframework.bridge.NativeActionRegistry
import com.duckframework.bridge.NativeCallback
import com.duckframework.bridge.NativeHost
import org.json.JSONArray
import org.json.JSONObject

/**
 * Version of the JavaScript/native protocol.
 *
 * Increase it only for breaking changes; new actions do not need a bump.
 */
const val BRIDGE_API_VERSION = 1

/**
 * Returns the bridge protocol version.
 *
 * Result: `{ "version": number, "platform": string }`.
 */
class BridgeInfoAction : NativeAction {

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        callback.success(
            JSONObject()
                .put("version", BRIDGE_API_VERSION)
                .put("platform", DeviceInfoAction.PLATFORM),
        )
    }
}

/**
 * Lists every action this build supports, so apps can feature-detect.
 *
 * Result: `{ "version": number, "capabilities": string[] }`.
 */
class BridgeCapabilitiesAction(
    private val registry: NativeActionRegistry,
) : NativeAction {

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        callback.success(
            JSONObject()
                .put("version", BRIDGE_API_VERSION)
                .put("capabilities", JSONArray(registry.names())),
        )
    }
}
