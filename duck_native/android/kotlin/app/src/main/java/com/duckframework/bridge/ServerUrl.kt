package com.duckframework.bridge

import java.util.Locale

/**
 * Parses and checks the server address typed on the connect screen.
 */
object ServerUrl {

    const val DEFAULT = "http://localhost:8000/"

    private const val HTTP_SCHEME = "http://"
    private const val HTTPS_SCHEME = "https://"
    private const val HTTP_DEFAULT_PORT = 80

    // Hosts allowed to use plain http; keep in sync with network_security_config.xml
    private val CLEARTEXT_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    // Hosts that `adb reverse` can forward to the computer running the server
    private val ADB_HOSTS = setOf("localhost", "127.0.0.1")

    /**
     * Outcome of checking typed input.
     */
    sealed class Result {
        /** A usable address, with a scheme added when it was missing. */
        data class Valid(val url: String) : Result()

        /** Unusable input; [messageRes] explains why. */
        data class Invalid(val messageRes: Int) : Result()
    }

    /**
     * Turns typed text into a server address.
     *
     * @param input Text from the address field.
     */
    fun parse(input: String): Result {
        val text = input.trim()
        if (text.isEmpty()) return Result.Invalid(R.string.error_invalid_url)

        // Add a scheme when missing: http for local hosts, https for the rest
        val url = if ("://" in text) text else schemeFor(text) + text
        val host = TrustPolicy.hostOf(url)
        if (host == null || TrustPolicy.originOf(url) == null) {
            return Result.Invalid(R.string.error_invalid_url)
        }

        // Plain http is only permitted to local hosts
        if (TrustPolicy.schemeOf(url) == "http" && host !in CLEARTEXT_HOSTS) {
            return Result.Invalid(R.string.error_cleartext_url)
        }
        return Result.Valid(url)
    }

    /**
     * Returns true when [url] points at this device or the emulator's host.
     */
    fun isLocal(url: String?): Boolean = TrustPolicy.hostOf(url) in CLEARTEXT_HOSTS

    /**
     * Returns the port to forward with `adb reverse`, or null when it does not apply.
     */
    fun adbPort(url: String?): Int? {
        if (TrustPolicy.hostOf(url) !in ADB_HOSTS) return null
        return TrustPolicy.portOf(url) ?: HTTP_DEFAULT_PORT
    }

    private fun schemeFor(text: String): String {
        val host = text.substringBefore('/').substringBefore(':').lowercase(Locale.ROOT)
        return if (host in CLEARTEXT_HOSTS) HTTP_SCHEME else HTTPS_SCHEME
    }
}
