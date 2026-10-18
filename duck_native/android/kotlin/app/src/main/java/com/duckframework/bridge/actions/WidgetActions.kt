package com.duckframework.bridge.actions

import com.duckframework.bridge.NativeAction
import com.duckframework.bridge.NativeCallback
import com.duckframework.bridge.NativeHost
import com.duckframework.bridge.Payload
import com.duckframework.bridge.WidgetManager
import org.json.JSONObject

private const val MAX_ID_LENGTH = 200
private const val MAX_NAME_LENGTH = 64

/**
 * Mounts a native widget.
 *
 * Payload: `{ "id": string, "name": string, "props": object }`.
 */
class WidgetMountAction(private val widgets: WidgetManager) : NativeAction {

    override fun execute(host: NativeHost, payload: JSONObject, callback: NativeCallback) {
        val id = Payload.requireString(payload, "id", MAX_ID_LENGTH)
        val name = Payload.requireString(payload, "name", MAX_NAME_LENGTH)
        widgets.mount(id, name, payload.optJSONObject("props") ?: JSONObject())
        callback.success()
    }
}

/**
 * Applies new props to a mounted widget.
 *
 * Payload: `{ "id": string, "props": object }`.
 */
class WidgetUpdateAction(private val widgets: WidgetManager) : NativeAction {

    override fun execute(host: NativeHost, payload: JSONObject, callback: NativeCallback) {
        val id = Payload.requireString(payload, "id", MAX_ID_LENGTH)
        widgets.update(id, payload.optJSONObject("props") ?: JSONObject())
        callback.success()
    }
}

/**
 * Places an inline widget over its placeholder element.
 *
 * Payload: `{ "id": string, "x": number, "y": number, "width": number,
 * "height": number, "visible": boolean }`, all in CSS pixels.
 */
class WidgetLayoutAction(private val widgets: WidgetManager) : NativeAction {

    override fun execute(host: NativeHost, payload: JSONObject, callback: NativeCallback) {
        val id = Payload.requireString(payload, "id", MAX_ID_LENGTH)
        widgets.layout(
            id = id,
            x = payload.optDouble("x", 0.0),
            y = payload.optDouble("y", 0.0),
            width = payload.optDouble("width", 0.0),
            height = payload.optDouble("height", 0.0),
            visible = payload.optBoolean("visible", true),
        )
        callback.success()
    }
}

/**
 * Removes a mounted widget.
 *
 * Payload: `{ "id": string }`.
 */
class WidgetUnmountAction(private val widgets: WidgetManager) : NativeAction {

    override fun execute(host: NativeHost, payload: JSONObject, callback: NativeCallback) {
        widgets.unmount(Payload.requireString(payload, "id", MAX_ID_LENGTH))
        callback.success()
    }
}
