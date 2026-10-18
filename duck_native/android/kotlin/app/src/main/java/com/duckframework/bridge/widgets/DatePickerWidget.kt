package com.duckframework.bridge.widgets

import android.app.DatePickerDialog
import com.duckframework.bridge.NativeWidget
import com.duckframework.bridge.WidgetContext
import com.duckframework.bridge.merge
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/**
 * A native date picker dialog.
 *
 * Props: `value`, `min`, `max` (all `YYYY-MM-DD`), `title`, and `show`, a
 * counter that opens the dialog each time it increases.
 * Events: `pick` with a `YYYY-MM-DD` string, `dismiss` with no value.
 */
class DatePickerWidget : NativeWidget {

    private lateinit var context: WidgetContext

    private val props = JSONObject()
    private var shown = 0L
    private var dialog: DatePickerDialog? = null

    override fun mount(context: WidgetContext, props: JSONObject) {
        this.context = context
        this.props.merge(props)

        // The first counter value is the baseline, so mounting never opens the dialog
        shown = this.props.optLong(KEY_SHOW, 0L)
    }

    override fun update(props: JSONObject) {
        this.props.merge(props)
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

        // Start on the current value, or today
        val start = parse(props.optString(KEY_VALUE)) ?: today()
        var picked = false

        val created = DatePickerDialog(
            context.host.activity,
            { _, year, month, day ->
                picked = true
                val text = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
                context.emit("pick", text)
            },
            start[0],
            start[1] - 1,
            start[2],
        )

        // Optional limits and title
        parse(props.optString(KEY_MIN))?.let { created.datePicker.minDate = millis(it, false) }
        parse(props.optString(KEY_MAX))?.let { created.datePicker.maxDate = millis(it, true) }
        props.optString(KEY_TITLE).takeIf { it.isNotEmpty() }?.let { created.setTitle(it) }

        created.setOnDismissListener {
            if (!picked) context.emit("dismiss")
        }
        dialog = created
        created.show()
    }

    // Returns [year, month, day], or null when the text is not a valid date
    private fun parse(text: String): IntArray? {
        val parts = text.split("-")
        if (parts.size != DATE_PARTS) return null

        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers[1] !in 1..MONTHS || numbers[2] !in 1..MAX_DAY) return null
        return intArrayOf(numbers[0], numbers[1], numbers[2])
    }

    private fun today(): IntArray {
        val now = Calendar.getInstance()
        return intArrayOf(
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun millis(date: IntArray, endOfDay: Boolean): Long {
        val calendar = Calendar.getInstance()
        if (endOfDay) {
            calendar.set(date[0], date[1] - 1, date[2], LAST_HOUR, LAST_MINUTE, LAST_SECOND)
        } else {
            calendar.set(date[0], date[1] - 1, date[2], 0, 0, 0)
        }
        return calendar.timeInMillis
    }

    private companion object {
        const val KEY_SHOW = "show"
        const val KEY_VALUE = "value"
        const val KEY_MIN = "min"
        const val KEY_MAX = "max"
        const val KEY_TITLE = "title"
        const val DATE_PARTS = 3
        const val MONTHS = 12
        const val MAX_DAY = 31
        const val LAST_HOUR = 23
        const val LAST_MINUTE = 59
        const val LAST_SECOND = 59
    }
}
