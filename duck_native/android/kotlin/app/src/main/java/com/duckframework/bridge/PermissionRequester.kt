package com.duckframework.bridge

import android.app.Activity
import android.content.pm.PackageManager

/**
 * Requests Android runtime permissions on demand.
 *
 * Each request gets its own code, so several can be pending at once and their
 * results cannot be mixed up. Nothing is requested at app start.
 *
 * The Activity must forward `onRequestPermissionsResult` to [onResult].
 */
class PermissionRequester(private val activity: Activity) {

    private val pending = HashMap<Int, (Boolean) -> Unit>()
    private var nextCode = FIRST_REQUEST_CODE

    /**
     * Ensures [permission] is granted, asking the user only if needed.
     *
     * Must be called on the main thread.
     *
     * @param permission Android permission name, for example `CAMERA`.
     * @param onResult Called with true when the permission is granted.
     */
    fun request(permission: String, onResult: (granted: Boolean) -> Unit) {
        // Skip the dialog when the permission is already granted
        val current = activity.checkSelfPermission(permission)
        if (current == PackageManager.PERMISSION_GRANTED) {
            onResult(true)
            return
        }

        // Ask, keyed by a per-request code
        val code = nextCode
        nextCode =
            if (nextCode >= LAST_REQUEST_CODE) FIRST_REQUEST_CODE else nextCode + 1
        pending[code] = onResult
        activity.requestPermissions(arrayOf(permission), code)
    }

    /**
     * Completes the request that used [requestCode].
     *
     * @param requestCode Code passed to the system dialog.
     * @param grantResults Grant results from the system.
     */
    fun onResult(requestCode: Int, grantResults: IntArray) {
        val callback = pending.remove(requestCode) ?: return

        // An empty result means the dialog was cancelled
        callback(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
    }

    companion object {
        const val FIRST_REQUEST_CODE = 1000
        const val LAST_REQUEST_CODE = 65_000
    }
}

/**
 * Runs [onGranted] once [permission] is granted, or fails the request.
 *
 * This is the standard shape for an action that needs a runtime permission:
 * check, ask if missing, then continue or answer PERMISSION_DENIED.
 *
 * @param permission Android permission name.
 * @param callback The request to fail when the permission is denied.
 * @param onGranted Continues the action.
 */
fun NativeHost.requirePermission(
    permission: String,
    callback: NativeCallback,
    onGranted: () -> Unit,
) {
    permissions.request(permission) { granted ->
        if (granted) {
            onGranted()
        } else {
            callback.error(ErrorCode.PERMISSION_DENIED, "Permission was denied")
        }
    }
}
