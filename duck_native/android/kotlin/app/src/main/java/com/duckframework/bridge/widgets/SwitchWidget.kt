package com.duckframework.bridge.widgets

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Switch
import com.duckframework.bridge.NativeWidget
import com.duckframework.bridge.WidgetContext
import org.json.JSONObject

/**
 * An inline Android Switch.
 *
 * Props: `checked` (boolean), `enabled` (boolean).
 * Events: `change` with the new checked state.
 */
class SwitchWidget : NativeWidget {

    private lateinit var context: WidgetContext
    private lateinit var view: Switch

    // True while props are being applied, so Python's own change is not echoed back
    private var applying = false

    override fun mount(context: WidgetContext, props: JSONObject) {
        this.context = context

        // Stay hidden until the first layout says where to go
        view = Switch(context.host.activity).apply {
            visibility = View.INVISIBLE
            setOnCheckedChangeListener { _, checked ->
                if (!applying) context.emit("change", checked)
            }
        }
        context.overlay.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        update(props)
    }

    override fun update(props: JSONObject) {
        applying = true
        if (props.has("checked")) view.isChecked = props.optBoolean("checked")
        if (props.has("enabled")) view.isEnabled = props.optBoolean("enabled", true)
        applying = false
    }

    override fun layout(x: Int, y: Int, width: Int, height: Int, visible: Boolean) {
        val params = view.layoutParams as FrameLayout.LayoutParams
        params.width = width
        params.height = height
        params.leftMargin = x
        params.topMargin = y
        view.layoutParams = params
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    override fun unmount() {
        context.overlay.removeView(view)
    }
}
