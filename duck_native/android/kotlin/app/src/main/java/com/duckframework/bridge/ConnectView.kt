package com.duckframework.bridge

import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * The launch and error screens that cover the WebView.
 *
 * While a page loads, the splash screen is shown with a slim progress bar. When
 * loading fails, an error card shows a readable message plus either an address
 * field or a single retry button.
 *
 * @param root The error overlay view from `activity_main.xml`.
 * @param splash The splash overlay view from `activity_main.xml`.
 * @param listener Receives the user's actions.
 * @param showTests Whether to offer the built-in bridge test page.
 */
class ConnectView(
    private val root: View,
    private val splash: View,
    private val listener: Listener,
    private val showTests: Boolean,
) {

    /**
     * Actions the user can take on this screen.
     */
    interface Listener {
        /** Connect to the address the user typed. */
        fun onConnect(input: String)

        /** Reload the page that failed. */
        fun onRetry()

        /** Open the built-in bridge test page. */
        fun onOpenTests()
    }

    private val title: TextView = root.findViewById(R.id.connect_title)
    private val message: TextView = root.findViewById(R.id.connect_message)
    private val detail: TextView = root.findViewById(R.id.connect_detail)
    private val form: View = root.findViewById(R.id.connect_form)
    private val input: EditText = root.findViewById(R.id.server_input)
    private val inputError: TextView = root.findViewById(R.id.input_error)
    private val primary: Button = root.findViewById(R.id.primary_button)
    private val tests: Button = root.findViewById(R.id.tests_button)
    private val notice: View = root.findViewById(R.id.trust_notice)

    private var canEdit = false

    init {
        primary.setOnClickListener { submit() }
        tests.setOnClickListener { listener.onOpenTests() }

        // The keyboard's Go key behaves like the Connect button
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                submit()
                true
            } else {
                false
            }
        }
    }

    /**
     * Shows the splash screen while a page loads.
     */
    fun showConnecting() {
        hideKeyboard()
        root.visibility = View.GONE
        present(splash)
    }

    /**
     * Shows a failure with the way to recover.
     *
     * @param error What to tell the user.
     * @param canEditServer True to offer an address field, false for retry only.
     * @param serverText Address to prefill in the field.
     */
    fun showError(error: ConnectionError, canEditServer: Boolean, serverText: String) {
        canEdit = canEditServer
        hideKeyboard()
        title.text = error.title
        message.text = error.message
        detail.text = error.detail
        detail.visibility = if (error.detail == null) View.GONE else View.VISIBLE
        splash.visibility = View.GONE

        // The address field only exists when the user may change servers
        form.visibility = if (canEditServer) View.VISIBLE else View.GONE
        inputError.visibility = View.GONE
        if (canEditServer) input.setText(serverText)

        primary.setText(if (canEditServer) R.string.connect else R.string.try_again)
        primary.visibility = View.VISIBLE
        tests.visibility = if (canEditServer && showTests) View.VISIBLE else View.GONE
        notice.visibility = if (canEditServer) View.VISIBLE else View.GONE
        present(root)
    }

    /**
     * Shows a problem with the typed address under the field.
     *
     * @param messageRes String resource explaining the problem.
     */
    fun showInputError(messageRes: Int) {
        inputError.setText(messageRes)
        inputError.visibility = View.VISIBLE
    }

    /**
     * Fades the screens away to reveal the page.
     */
    fun hide() {
        fadeOut(splash)
        fadeOut(root)
    }

    private fun submit() {
        if (canEdit) {
            inputError.visibility = View.GONE
            listener.onConnect(input.text.toString())
        } else {
            listener.onRetry()
        }
    }

    private fun present(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.visibility = View.VISIBLE
    }

    private fun fadeOut(view: View) {
        if (view.visibility != View.VISIBLE) return
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(FADE_MS)
            .withEndAction { view.visibility = View.GONE }
            .start()
    }

    private fun hideKeyboard() {
        input.clearFocus()
        val manager = root.context.getSystemService(InputMethodManager::class.java)
        manager?.hideSoftInputFromWindow(root.windowToken, 0)
    }

    private companion object {
        const val FADE_MS = 150L
    }
}
