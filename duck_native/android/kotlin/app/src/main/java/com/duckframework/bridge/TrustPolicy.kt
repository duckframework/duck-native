package com.duckframework.bridge

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Decides which pages may use the native bridge.
 *
 * Trust is decided by origin (scheme, host and port). It is checked in Kotlin
 * for every request and every message sent back, so JavaScript cannot skip it.
 *
 * @param trustedUrls URLs or origins to trust; only their origins are used.
 */
class TrustPolicy(trustedUrls: Collection<String>) {

    @Volatile
    private var origins: Set<String> = originsOf(trustedUrls)

    /**
     * Replaces the trusted origins, for example after connecting to a new server.
     *
     * @param trustedUrls URLs or origins to trust; only their origins are used.
     */
    fun setTrusted(trustedUrls: Collection<String>) {
        origins = originsOf(trustedUrls)
    }

    /**
     * Returns true when [url] belongs to a trusted origin.
     *
     * @param url Full URL of the current page, or null when unknown.
     */
    fun isTrusted(url: String?): Boolean {
        val origin = originOf(url) ?: return false
        return origin in origins
    }

    companion object {
        private const val HTTP = "http"
        private const val HTTPS = "https"
        private const val HTTP_DEFAULT_PORT = 80
        private const val HTTPS_DEFAULT_PORT = 443

        private fun originsOf(urls: Collection<String>): Set<String> =
            urls.mapNotNull { originOf(it) }.toSet()

        /**
         * Returns the lowercase host of [url], or null when it cannot be parsed.
         */
        fun hostOf(url: String?): String? = parse(url)?.host?.lowercase(Locale.ROOT)

        /**
         * Returns the explicit port of [url], or null when none is given.
         */
        fun portOf(url: String?): Int? = parse(url)?.port?.takeIf { it != -1 }

        /**
         * Returns the lowercase scheme of [url], or null when it cannot be parsed.
         */
        fun schemeOf(url: String): String? =
            parse(url)?.scheme?.lowercase(Locale.ROOT)

        /**
         * Normalizes [url] to `scheme://host[:port]`.
         *
         * Returns null for anything that is not a plain http(s) URL with a host.
         * URLs with embedded credentials and hosts that `java.net.URI` rejects are
         * refused, which closes common parser-confusion tricks.
         */
        fun originOf(url: String?): String? {
            val uri = parse(url) ?: return null
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            if (scheme != HTTP && scheme != HTTPS) return null
            if (uri.userInfo != null) return null
            val host = uri.host?.lowercase(Locale.ROOT) ?: return null

            // Drop the port when it is the scheme default
            val defaultPort =
                if (scheme == HTTPS) HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT
            val port = if (uri.port == -1) defaultPort else uri.port
            val portSuffix = if (port == defaultPort) "" else ":$port"

            return "$scheme://$host$portSuffix"
        }

        private fun parse(url: String?): URI? {
            if (url == null) return null
            return try {
                URI(url)
            } catch (error: URISyntaxException) {
                null
            }
        }
    }
}
