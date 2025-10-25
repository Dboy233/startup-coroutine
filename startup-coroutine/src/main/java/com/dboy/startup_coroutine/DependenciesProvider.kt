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
     * This method assumes the dependency has successfully completed and returned a non-null result.
     * It will throw an `IllegalStateException` if the result is not available, which can happen if:
     * - The dependency has not been executed yet.
     * - The dependency failed during execution.
     * - The dependency's result is `null` or `Unit`.
     * - The dependency was not declared in the `initializers` list for the `Startup` instance.
     *
     * **For safer, non-crashing access, it is strongly recommended to use [resultOrNull].**
     *
     * --- (中文说明) ---
     *
     * 获取一个已完成依赖项的初始化结果。
     *
     * 此方法假定依赖项已经成功完成并返回了一个非空结果。如果结果不可用，它将抛出 `IllegalStateException` 异常。
     * 结果不可用的情况包括：
     * - 依赖项尚未执行。
     * - 依赖项在执行过程中失败。
     * - 依赖项的返回结果是 `null` 或 `Unit`。
     * - 依赖项未在 `Startup` 实例的 `initializers` 列表中声明。
     *
     * **为了更安全、避免崩溃的访问方式，强烈建议使用 [resultOrNull] 方法。**
     *
     * @param T The expected type of the result. (期望返回的结果类型。)
     * @param dependency The [KClass] of the target dependency `Initializer`. (目标依赖项 `Initializer` 的 `KClass`。)
     * @return The non-null result of the dependency. (依赖项的初始化结果，非空。)
     * @throws IllegalStateException if the result is not available for any of the reasons listed above. (如果因上述任何原因导致结果不可用。)
     */
    fun <T> result(dependency: KClass<out Initializer<*>>): T

    /**
     * Safely retrieves the result of a completed dependency, returning `null` if it's unavailable.
     *
     * This is the **recommended** method for accessing dependency results. It provides a safe way to
     * handle cases where a result might not be present, without causing a crash. It returns `null` if:
     * - The dependency has not been executed yet.
     * - The dependency failed during execution.
     * - The dependency returned a `null` or `Unit` value.
     * - The dependency was not found in the startup sequence.
     *
     * --- (中文说明) ---
     *
     * 安全地获取一个已完成依赖项的初始化结果，如果结果不可用则返回 `null`。
     *
     * 这是**推荐**的依赖结果访问方法。它提供了一种无需使程序崩溃即可安全处理结果不存在情况的方式。
     * 在以下几种情况中，它会返回`null`:
     * - 依赖项尚未执行。
     * - 依赖项在执行过程中失败。
     * - 依赖项的返回值为 `null` 或 `Unit`。
     * - 依赖项未在启动序列中找到。
     *
     * @param T The expected type of the result. (期望返回的结果类型。)
     * @param dependency The [KClass] of the target dependency `Initializer`. (目标依赖项 `Initializer` 的 `KClass`。)
     * @return The result of the dependency, or `null` if it is not available for any reason. (依赖项的初始化结果，如果因任何原因不可用则为 `null`。)
     */
    fun <T> resultOrNull(dependency: KClass<out Initializer<*>>): T?
}
