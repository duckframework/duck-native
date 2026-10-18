package com.duckframework.bridge

import android.widget.FrameLayout
import org.json.JSONObject

/**
 * What a widget may use from the app.
 *
 * @property host App services, including the Activity.
 * @property overlay Layer above the WebView where inline widgets are drawn.
 * @property density Screen density, used to convert CSS pixels to pixels.
 */
class WidgetContext(
    val host: NativeHost,
    val overlay: FrameLayout,
    val density: Float,
    private val sender: (String, Any?) -> Unit,
) {

    /**
     * Sends an event to the Python widget.
     *
     * @param event Event name, for example `change`. Python receives it as `native-change`.
     * @param value JSON-compatible value passed to the Python handler.
     */
    fun emit(event: String, value: Any? = null) = sender(event, value)
}

/**
 * A native Android widget driven by a Python component.
 *
 * Every call runs on the main thread. Props arrive as the Python widget's full
 * `native_props`, so [update] must apply only what changed.
 */
interface NativeWidget {

    /**
     * Creates the widget's views.
     *
     * @param context App services and the event channel.
     * @param props The Python widget's initial props.
     */
    fun mount(context: WidgetContext, props: JSONObject)

    /**
     * Applies new props.
     *
     * @param props Every prop, changed or not.
     */
    fun update(props: JSONObject)

    /**
     * Places an inline widget over its placeholder. Overlay widgets ignore it.
     *
     * @param x Left edge in pixels.
     * @param y Top edge in pixels.
     * @param width Width in pixels.
     * @param height Height in pixels.
     * @param visible False when the placeholder is hidden or scrolled out of view.
     */
    fun layout(x: Int, y: Int, width: Int, height: Int, visible: Boolean) {
    }

    /**
     * Removes the widget's views and closes anything it opened.
     */
    fun unmount()
}

/**
 * Copies every key of [changes] into this object.
 */
fun JSONObject.merge(changes: JSONObject) {
    for (key in changes.keys()) {
        put(key, changes.get(key))
    }
}
