package com.duckframework.bridge

import android.widget.FrameLayout
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Owns the widgets mounted by one WebView.
 *
 * Widgets are keyed by the id of their Python component. Events go back to the
 * page through the [PageChannel] as `widget` events. Call [release] when the
 * WebView is destroyed so no widget outlives its page.
 *
 * @param host App services handed to every widget.
 * @param overlay Layer above the WebView where inline widgets are drawn.
 * @param channel Delivers widget events to the page.
 * @param registry Widgets that may be mounted.
 */
class WidgetManager(
    private val host: NativeHost,
    private val overlay: FrameLayout,
    private val channel: PageChannel,
    private val registry: NativeWidgetRegistry,
) {

    private val widgets = LinkedHashMap<String, NativeWidget>()

    /**
     * Creates and mounts a widget, replacing any widget with the same id.
     *
     * @param id Id of the Python component.
     * @param name Registered widget name.
     * @param props Initial props.
     */
    fun mount(id: String, name: String, props: JSONObject) {
        val widget = registry.create(name)
            ?: throw NativeException(ErrorCode.NOT_SUPPORTED, "Unknown widget: $name")

        // Replace a widget left over under the same id
        widgets.remove(id)?.unmount()

        val context = WidgetContext(host, overlay, overlay.resources.displayMetrics.density) { event, value ->
            emit(id, event, value)
        }
        widget.mount(context, props)
        widgets[id] = widget
    }

    /**
     * Applies new props to a mounted widget. Unknown ids are ignored.
     */
    fun update(id: String, props: JSONObject) {
        widgets[id]?.update(props)
    }

    /**
     * Places an inline widget, converting CSS pixels to pixels.
     */
    fun layout(id: String, x: Double, y: Double, width: Double, height: Double, visible: Boolean) {
        val density = overlay.resources.displayMetrics.density
        widgets[id]?.layout(
            (x * density).roundToInt(),
            (y * density).roundToInt(),
            (width * density).roundToInt(),
            (height * density).roundToInt(),
            visible,
        )
    }

    /**
     * Removes a widget. Unknown ids are ignored.
     */
    fun unmount(id: String) {
        widgets.remove(id)?.unmount()
    }

    /**
     * Removes every widget.
     */
    fun release() {
        widgets.values.forEach { it.unmount() }
        widgets.clear()
    }

    private fun emit(id: String, event: String, value: Any?) {
        val message = JSONObject()
            .put("id", id)
            .put("event", event)
            // JSONObject.put(key, null) would delete the key, so use NULL
            .put("value", value ?: JSONObject.NULL)
        channel.emit("widget", message)
    }
}
