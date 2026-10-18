package com.duckframework.bridge

import com.duckframework.bridge.widgets.BottomSheetWidget
import com.duckframework.bridge.widgets.DatePickerWidget
import com.duckframework.bridge.widgets.SwitchWidget

/**
 * The allowlist of native widgets a page can mount.
 *
 * To add a widget, write a [NativeWidget] and register it in [createDefault],
 * or annotate it with [DuckWidget] in a project's native/widgets folder.
 */
class NativeWidgetRegistry {

    private val factories = LinkedHashMap<String, () -> NativeWidget>()

    /**
     * Adds a widget under a lowercase name such as `switch`.
     *
     * @param name Lowercase name, matching the Python widget's NAME.
     * @param factory Creates a fresh widget for every mount.
     */
    fun register(name: String, factory: () -> NativeWidget) {
        require(NAME_PATTERN.matches(name)) { "Invalid widget name: $name" }
        check(name !in factories) { "Widget already registered: $name" }
        factories[name] = factory
    }

    /**
     * Creates a widget by name, or returns null when none is registered.
     */
    fun create(name: String): NativeWidget? = factories[name]?.invoke()

    companion object {
        private val NAME_PATTERN = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")

        /**
         * Builds the registry used by the app.
         */
        fun createDefault(): NativeWidgetRegistry {
            val registry = NativeWidgetRegistry()

            // Widgets available to every Duck application
            registry.register("switch") { SwitchWidget() }
            registry.register("date_picker") { DatePickerWidget() }
            registry.register("bottom_sheet") { BottomSheetWidget() }

            // Widgets from the project's native/widgets folder
            registerCustomWidgets(registry)

            return registry
        }
    }
}
