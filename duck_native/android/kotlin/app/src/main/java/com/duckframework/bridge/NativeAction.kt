package com.duckframework.bridge

import android.app.Activity
import org.json.JSONObject

/**
 * Stable error codes returned to JavaScript.
 *
 * These names are part of the public protocol, so never rename them.
 * [TIMEOUT] is raised by the JavaScript client when a call exceeds its
 * `timeoutMs` option; it is listed here to keep every code in one place.
 */
enum class ErrorCode {
    INVALID_REQUEST,
    INVALID_PAYLOAD,
    UNKNOWN_ACTION,
    PERMISSION_DENIED,
    NOT_SUPPORTED,
    OPERATION_CANCELLED,
    NATIVE_ERROR,
    TIMEOUT,
}

/**
 * Thrown by an action to fail its request with a specific [ErrorCode].
 *
 * The message is sent to JavaScript, so never put stack traces or internal
 * details in it.
 */
class NativeException(val code: ErrorCode, message: String) : Exception(message)

/**
 * Completes one native request.
 *
 * Only the first call to [success] or [error] takes effect. It may be called
 * from any thread, and long after [NativeAction.execute] has returned.
 */
interface NativeCallback {

    /**
     * Resolves the JavaScript Promise.
     *
     * @param result Structured result, or null when there is nothing to return.
     */
    fun success(result: JSONObject? = null)

    /**
     * Rejects the JavaScript Promise.
     *
     * @param code Stable error code.
     * @param message Short, safe description for JavaScript.
     */
    fun error(code: ErrorCode, message: String)
}

/**
 * What an action may use from the app.
 *
 * Actions receive this instead of the Activity alone, so the bridge never has to
 * grow a new constructor argument for every capability.
 */
class NativeHost(
    val activity: Activity,
    val permissions: PermissionRequester,
)

/**
 * One allowlisted native capability.
 *
 * Actions run on the main thread. They must not block; anything slow or
 * interactive should return immediately and finish later through the callback.
 */
interface NativeAction {

    /**
     * Runs the action.
     *
     * @param host App services the action may use.
     * @param payload Request payload. Validate every field you read.
     * @param callback Completes the request, now or later.
     */
    fun execute(host: NativeHost, payload: JSONObject, callback: NativeCallback)
}
