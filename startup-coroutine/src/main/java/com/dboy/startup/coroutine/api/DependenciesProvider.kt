package com.dboy.startup.coroutine.api

import kotlin.reflect.KClass

/**
 * Provides access to the results of initialization tasks that have already completed.
 * This interface is passed to the [Initializer.init] method, allowing a task to retrieve
 * the return values of its dependencies.
 *
 * --- (中文说明) ---
 *
 * 提供对已完成的初始化任务结果的访问能力。
 * 此接口会传递给 [Initializer.init] 方法，允许当前任务获取其依赖项的返回值。
 */
interface DependenciesProvider {

    /**
     * Retrieves the result of a completed dependency task.
     * Throws an exception if the result is not found or the type does not match.
     *
     * Use this method when the dependency is mandatory and you expect a non-null result.
     *
     * --- (中文说明) ---
     *
     * 获取一个已完成的依赖任务的结果。
     * 如果找不到结果或类型不匹配，则抛出异常。
     *
     * 当依赖项是必须的且您期望结果不为 null 时，请使用此方法。
     *
     * @param T The type of the result expected.
     *          <br>期望的结果类型。
     * @param dependency The class of the dependency [Initializer].
     *                   <br>依赖项 [Initializer] 的类。
     * @return The result object returned by the dependency's [Initializer.init] method.
     *         <br>依赖项 [Initializer.init] 方法返回的结果对象。
     * @throws IllegalStateException If the result is not found (e.g., the dependency was not initialized or failed).
     *                               <br>如果找不到结果（例如，依赖项未初始化或失败），则抛出此异常。
     */
    fun <T> result(dependency: KClass<out Initializer<*>>): T

    /**
     * Safely retrieves the result of a completed dependency task, returning null if not found.
     *
     * Use this method when the dependency might not have produced a result, or if you want to handle missing dependencies gracefully.
     *
     * --- (中文说明) ---
     *
     * 安全地获取一个已完成的依赖任务的结果，如果未找到则返回 null。
     *
     * 当依赖项可能没有产生结果，或者您希望优雅地处理缺失的依赖项时，请使用此方法。
     *
     * @param T The type of the result expected.
     *          <br>期望的结果类型。
     * @param dependency The class of the dependency [Initializer].
     *                   <br>依赖项 [Initializer] 的类。
     * @return The result object, or null if it doesn't exist.
     *         <br>结果对象，如果不存在则返回 null。
     */
    fun <T> resultOrNull(dependency: KClass<out Initializer<*>>): T?
}
