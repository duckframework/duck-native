package com.duckframework.bridge

import android.content.Context

/**
 * Remembers the server the user last connected to successfully.
 *
 * @param context Used to reach private preferences.
 */
class ServerStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /**
     * The last server that loaded, or null when none has yet.
     */
    var lastUrl: String?
        get() = prefs.getString(KEY_LAST_URL, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_URL, value).apply()
        }

    private companion object {
        const val NAME = "duck_bridge"
        const val KEY_LAST_URL = "last_url"
    }
}
