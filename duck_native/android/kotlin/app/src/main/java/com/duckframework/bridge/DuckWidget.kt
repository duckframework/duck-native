package com.duckframework.bridge

/**
 * Marks a [NativeWidget] written in a project's native/widgets folder.
 *
 * Duck Native registers the class under [name] when it builds, for example
 * `@DuckWidget("rating")`. The annotation must sit on its own line, directly
 * above a class with a no-argument constructor.
 *
 * @property name Lowercase widget name, matching the Python widget's NAME.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class DuckWidget(val name: String)
