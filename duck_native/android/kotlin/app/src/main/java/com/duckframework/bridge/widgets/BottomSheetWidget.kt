package com.duckframework.bridge.widgets

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.duckframework.bridge.NativeWidget
import com.duckframework.bridge.R
import com.duckframework.bridge.WidgetContext
import com.duckframework.bridge.merge
import org.json.JSONObject

/**
 * A native bottom sheet with a title, a message and action buttons.
 *
 * Props: `title`, `message`, `actions` (list of `{id, label}`), `dismissible`
 * (boolean), and the counters `show` and `hide`, which open and close the
 * sheet each time they increase.
 * Events: `action` with `{id}`, `dismiss` when closed without choosing.
 */
class BottomSheetWidget : NativeWidget {

    private lateinit var widgetContext: WidgetContext

    private val props = JSONObject()
    private var shown = 0L
    private var hidden = 0L
    private var finished = false
    private var dialog: Dialog? = null

    override fun mount(context: WidgetContext, props: JSONObject) {
        this.widgetContext = context
        this.props.merge(props)

        // The first counter values are the baseline, so mounting never opens the sheet
        shown = this.props.optLong(KEY_SHOW, 0L)
        hidden = this.props.optLong(KEY_HIDE, 0L)
    }

    override fun update(props: JSONObject) {
        this.props.merge(props)

        val hide = this.props.optLong(KEY_HIDE, hidden)
        if (hide > hidden) {
            hidden = hide
            finished = true
            dialog?.dismiss()
        }

        val show = this.props.optLong(KEY_SHOW, shown)
        if (show > shown) {
            shown = show
            present()
        }
    }

    override fun unmount() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    private fun present() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        finished = false

        val activity = widgetContext.host.activity
        val created = Dialog(activity)
        created.requestWindowFeature(Window.FEATURE_NO_TITLE)
        created.setContentView(buildContent(created))
        created.setCancelable(props.optBoolean(KEY_DISMISSIBLE, true))

        // Pin the sheet to the bottom edge with a slide-in animation
        created.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }

        created.setOnDismissListener {
            if (!finished) widgetContext.emit("dismiss")
        }
        dialog = created
        created.show()
    }

    private fun buildContent(sheet: Dialog): LinearLayout {
        val activity = widgetContext.host.activity
        val padding = pixels(PADDING_DP)

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                setColor(activity.getColor(R.color.card))
                val corner = pixels(CORNER_DP).toFloat()
                cornerRadii = floatArrayOf(corner, corner, corner, corner, 0f, 0f, 0f, 0f)
            }
        }

        // Title and message
        props.optString(KEY_TITLE).takeIf { it.isNotEmpty() }?.let {
            column.addView(label(it, TITLE_SP, R.color.text_primary, bold = true))
        }
        props.optString(KEY_MESSAGE).takeIf { it.isNotEmpty() }?.let {
            val view = label(it, MESSAGE_SP, R.color.text_secondary, bold = false)
            (view.layoutParams as LinearLayout.LayoutParams).topMargin = pixels(GAP_DP)
            column.addView(view)
        }

        // One button per action, the first one highlighted
        for ((index, action) in readActions().withIndex()) {
            val style = if (index == 0) R.style.Widget_Duck_Button_Primary else R.style.Widget_Duck_Button_Text
            val button = Button(activity, null, 0, style).apply {
                text = action.second
                setOnClickListener {
                    finished = true
                    widgetContext.emit("action", JSONObject().put("id", action.first))
                    sheet.dismiss()
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            params.topMargin = pixels(GAP_DP)
            column.addView(button, params)
        }
        return column
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView {
        val view = TextView(widgetContext.host.activity)
        view.text = text
        view.textSize = size
        view.setTextColor(widgetContext.host.activity.getColor(color))
        if (bold) view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
        view.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return view
    }

    // Returns (id, label) pairs, skipping invalid entries; a lone Close button when empty
    private fun readActions(): List<Pair<String, String>> {
        val found = mutableListOf<Pair<String, String>>()
        val array = props.optJSONArray(KEY_ACTIONS)

        for (index in 0 until minOf(array?.length() ?: 0, MAX_ACTIONS)) {
            val item = array?.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val text = item.optString("label")
            if (id.isNotEmpty() && text.isNotEmpty() && text.length <= MAX_LABEL) {
                found.add(id to text)
            }
        }
        if (found.isEmpty()) {
            found.add(CLOSE_ID to widgetContext.host.activity.getString(R.string.close))
        }
        return found
    }

    private fun pixels(dp: Int): Int = (dp * widgetContext.density).toInt()

    private companion object {
        const val KEY_SHOW = "show"
        const val KEY_HIDE = "hide"
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_ACTIONS = "actions"
        const val KEY_DISMISSIBLE = "dismissible"
        const val CLOSE_ID = "close"
        const val MAX_ACTIONS = 6
        const val MAX_LABEL = 60
        const val PADDING_DP = 24
        const val CORNER_DP = 20
        const val GAP_DP = 8
        const val TITLE_SP = 20f
        const val MESSAGE_SP = 15f
    }
}
