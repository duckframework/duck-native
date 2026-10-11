package com.duckframework.bridge

import org.json.JSONObject

/**
 * Validation helpers for request payloads.
 *
 * Every helper throws [NativeException] with [ErrorCode.INVALID_PAYLOAD], which
 * the bridge turns into a structured error response. They read values with
 * `opt` and check the type, because `optString` turns JSON null into "null".
 */
object Payload {

    /**
     * Reads a required, non-empty string.
     *
     * @param payload Request payload.
     * @param key Field name.
     * @param maxLength Longest accepted string.
     * @return The validated string.
     */
    fun requireString(payload: JSONObject, key: String, maxLength: Int): String {
        val value = payload.opt(key)
        if (value !is String || value.isEmpty()) {
            throw NativeException(
                ErrorCode.INVALID_PAYLOAD,
                "'$key' must be a non-empty string",
            )
        }
        if (value.length > maxLength) {
            throw NativeException(ErrorCode.INVALID_PAYLOAD, "'$key' is too long")
        }
        return value
    }

    /**
     * Reads an optional string.
     *
     * @param payload Request payload.
     * @param key Field name.
     * @param maxLength Longest accepted string.
     * @return The string, or null when the field is absent.
     */
    fun optionalString(payload: JSONObject, key: String, maxLength: Int): String? {
        if (!payload.has(key)) return null
        return requireString(payload, key, maxLength)
    }

    /**
     * Reads an optional whole number within a range.
     *
     * @param payload Request payload.
     * @param key Field name.
     * @param fallback Value used when the field is absent.
     * @param min Smallest accepted value.
     * @param max Largest accepted value.
     * @return The validated number.
     */
    fun optionalLong(
        payload: JSONObject,
        key: String,
        fallback: Long,
        min: Long,
        max: Long,
    ): Long {
        val value = payload.opt(key) ?: return fallback
        if (value !is Number) {
            throw NativeException(ErrorCode.INVALID_PAYLOAD, "'$key' must be a number")
        }
        val number = value.toLong()
        if (number !in min..max) {
            throw NativeException(
                ErrorCode.INVALID_PAYLOAD,
                "'$key' must be between $min and $max",
            )
        }
        return number
    }
}
