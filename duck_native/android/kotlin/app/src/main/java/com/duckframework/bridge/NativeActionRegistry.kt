package com.duckframework.bridge

import com.duckframework.bridge.actions.BridgeCapabilitiesAction
import com.duckframework.bridge.actions.BridgeInfoAction
import com.duckframework.bridge.actions.ClipboardReadAction
import com.duckframework.bridge.actions.ClipboardWriteAction
import com.duckframework.bridge.actions.DebugDelayAction
import com.duckframework.bridge.actions.DeviceInfoAction
import com.duckframework.bridge.actions.ShareAction
import com.duckframework.bridge.actions.ToastAction
import com.duckframework.bridge.actions.VibrateAction

/**
 * The allowlist of native actions reachable from JavaScript.
 *
 * If an action is not registered here, JavaScript cannot invoke it. To add a
 * capability, write a [NativeAction] and register it in [createDefault].
 */
class NativeActionRegistry {

    private val actions = LinkedHashMap<String, NativeAction>()

    /**
     * Adds an action under a namespaced name such as `clipboard.read`.
     *
     * @param name Lowercase dotted name.
     * @param action Implementation.
     */
    fun register(name: String, action: NativeAction) {
        require(NAME_PATTERN.matches(name)) { "Invalid action name: $name" }
        check(name !in actions) { "Action already registered: $name" }
        actions[name] = action
    }

    /**
     * Returns the action registered under [name], or null when there is none.
     */
    fun find(name: String): NativeAction? = actions[name]

    /**
     * Returns every registered action name, in registration order.
     */
    fun names(): List<String> = actions.keys.toList()

    companion object {
        private val NAME_PATTERN = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")

        /**
         * Builds the registry used by the app.
         *
         * @param debuggable Whether to include debug-only actions.
         */
        fun createDefault(debuggable: Boolean): NativeActionRegistry {
            val registry = NativeActionRegistry()

            // Capabilities available to every Duck application
            registry.register("toast", ToastAction())
            registry.register("vibrate", VibrateAction())
            registry.register("clipboard.read", ClipboardReadAction())
            registry.register("clipboard.write", ClipboardWriteAction())
            registry.register("share", ShareAction())
            registry.register("device.info", DeviceInfoAction())

            // Bridge metadata
            registry.register("bridge.info", BridgeInfoAction())
            registry.register("bridge.capabilities", BridgeCapabilitiesAction(registry))

            // Test helpers, never shipped in release builds
            if (debuggable) {
                registry.register("debug.delay", DebugDelayAction())
            }

            return registry
        }
    }
}
