package com.duckframework.bridge

/**
 * Marks a [NativeAction] written in a project's native/actions folder.
 *
 * Duck Native registers the class under [name] when it builds, for example
 * `@DuckAction("flashlight.toggle")`. The annotation must sit on its own line.
 *
 * @property name Lowercase dotted action name.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class DuckAction(val name: String)
