package com.duckframework.bridge.actions

import android.content.ActivityNotFoundException
import android.content.Intent
import com.duckframework.bridge.ErrorCode
import com.duckframework.bridge.NativeAction
import com.duckframework.bridge.NativeCallback
import com.duckframework.bridge.NativeHost
import com.duckframework.bridge.Payload
import org.json.JSONObject

/**
 * Opens the Android share sheet with plain text.
 *
 * Payload: `{ "text": string, "title": string }`, where title is optional.
 * Resolves once the sheet is shown; Android does not report whether the user
 * finished sharing.
 */
class ShareAction : NativeAction {

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        // Validate the payload
        val text = Payload.requireString(payload, "text", MAX_TEXT_LENGTH)
        val title = Payload.optionalString(payload, "title", MAX_TITLE_LENGTH)

        // Build a fixed ACTION_SEND intent; JavaScript never supplies the intent
        val send = Intent(Intent.ACTION_SEND).apply {
            type = TEXT_MIME_TYPE
            putExtra(Intent.EXTRA_TEXT, text)
        }

        // Show the sheet
        try {
            host.activity.startActivity(Intent.createChooser(send, title))
        } catch (error: ActivityNotFoundException) {
            callback.error(ErrorCode.NOT_SUPPORTED, "No app can share text")
            return
        }

        callback.success()
    }

    companion object {
        const val MAX_TEXT_LENGTH = 50_000
        const val MAX_TITLE_LENGTH = 200
        const val TEXT_MIME_TYPE = "text/plain"
    }
}
