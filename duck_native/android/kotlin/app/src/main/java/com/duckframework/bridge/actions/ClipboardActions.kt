package com.duckframework.bridge.actions

import android.content.ClipData
import android.content.ClipboardManager
import com.duckframework.bridge.NativeAction
import com.duckframework.bridge.NativeCallback
import com.duckframework.bridge.NativeHost
import com.duckframework.bridge.Payload
import org.json.JSONObject

/**
 * Copies text to the clipboard.
 *
 * Payload: `{ "text": string }`.
 */
class ClipboardWriteAction : NativeAction {

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        // Validate the text
        val text = Payload.requireString(payload, "text", MAX_TEXT_LENGTH)

        // Copy it
        val clipboard = host.activity.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))

        callback.success()
    }

    companion object {
        const val MAX_TEXT_LENGTH = 100_000
        const val CLIP_LABEL = "Duck"
    }
}

/**
 * Reads text from the clipboard.
 *
 * Result: `{ "text": string }`, empty when the clipboard holds no text.
 * Only trusted pages can call this, because clipboards often hold secrets.
 */
class ClipboardReadAction : NativeAction {

    override fun execute(
        host: NativeHost,
        payload: JSONObject,
        callback: NativeCallback,
    ) {
        // Read the primary clip
        val clipboard = host.activity.getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip

        // Extract text, ignoring images and other clip types
        val text = clip
            ?.takeIf { it.itemCount > 0 }
            ?.takeIf { it.description.hasMimeType(TEXT_MIME_PATTERN) }
            ?.getItemAt(0)
            ?.coerceToText(host.activity)
            ?.toString()
            ?: ""

        callback.success(JSONObject().put("text", text))
    }

    companion object {
        const val TEXT_MIME_PATTERN = "text/*"
    }
}
