package com.dboy.startup_coroutine

import kotlin.reflect.KClass

/**
 * A provider that allows an [Initializer] to access the results of its dependencies.
 *
 * An instance of this interface is passed to the `Initializer.init()` method,
 * giving each task a way to retrieve the output from the tasks it depends on.
 *
 * --- (中文说明) ---
 *
 * 一个提供者接口，允许 [Initializer] 在其执行期间获取其依赖项的结果。
 *
 * 该接口的实例会作为参数传递给 `Initializer.init()` 方法，从而为每个任务提供了
 * 一种从其所依赖的任务中检索输出结果的途径。
 */
interface DependenciesProvider {
    /**
     * Retrieves the result of a completed dependency.
     *
     * This method is an "unsafe" operation because it will throw an exception if the dependency has not
     * yet completed, has failed, or is not declared in the `initializers` list.
     *
     * **It is highly recommended to use [resultOrNull] for safer access.**
     *
     * --- (中文说明) ---
     *
     * 获取一个已完成依赖项的初始化结果。
     *
     * 这是一个“不安全”的操作，因为如果依赖项尚未完成、执行失败、或未在启动任务列表中声明，
     * 此方法将抛出异常。
     *
     * **强烈建议优先使用 [resultOrNull] 以进行更安全的结果访问。**
     *
     * @param T The expected type of the result. (期望返回的结果类型。)
     * @param dependency The [KClass] of the target dependency `Initializer`. (目标依赖项 `Initializer` 的 `KClass`。)
     * @return The non-null result of the dependency. (依赖项的初始化结果，非空。)
     * @throws IllegalStateException if the dependency is not yet initialized, has failed,
     * or was never added to the startup sequence. (如果依赖项尚未初始化、执行失败或不存在于启动序列中。)
     */
    fun <T> result(dependency: KClass<out Initializer<*>>): T

    /**
     * Safely retrieves the result of a completed dependency, or `null` if it's unavailable.
     *
     * This is the **recommended** method for accessing dependency results, as it avoids
     * unexpected exceptions.
     *
     * --- (中文说明) ---
     *
     * 安全地获取一个已完成依赖项的初始化结果，如果结果不可用则返回 `null`。
     *
     * 这是**推荐**的依赖结果访问方法，因为它可以避免意外的异常。在以下几种情况中，它会返回`null`:
     * - 依赖项尚未执行完毕。
     * - 依赖项在执行过程中发生了错误。
     * - 依赖项未在启动序列中找到。
     * - 依赖项执行成功，但其返回值为 `null` 或 `Unit`。
     *
     * @param T The expected type of the result. (期望返回的结果类型。)
     * @param dependency The [KClass] of the target dependency `Initializer`. (目标依赖项 `Initializer` 的 `KClass`。)
     * @return The result of the dependency, or `null` if it is not available. (依赖项的初始化结果，如果不可用则为 `null`。)
     */
    fun <T> resultOrNull(dependency: KClass<out Initializer<*>>): T?
}
