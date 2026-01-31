package com.dboy.startup.coroutine.api

import android.app.Application
import kotlin.reflect.KClass

/**
 * Defines a unit of initialization work to be executed during the startup process.
 * Implement this interface to create a startup task.
 *
 * --- (中文说明) ---
 *
 * 定义启动过程中需要执行的一个初始化工作单元。
 * 实现此接口以创建一个启动任务。
 *
 * @param T The type of the result returned by the initialization task.
 *          If the task produces no result, use [Unit].
 *          <br>
 *          初始化任务返回的结果类型。如果任务不产生结果，请使用 [Unit]。
 */
interface Initializer<T> {

    /**
     * Performs the initialization logic.
     * This method is a suspending function, allowing you to perform long-running operations
     * (e.g., I/O) without blocking the main thread, provided you switch dispatchers internally.
     *
     * --- (中文说明) ---
     *
     * 执行初始化逻辑。
     * 这是一个挂起函数，允许你在内部执行耗时操作（例如 I/O），而不会阻塞主线程
     * （前提是你需要在内部根据情况切换调度器，或者依赖框架的调度配置）。
     *
     * @param application The Application context.
     *                    <br>
     *                    Application 上下文对象。
     * @param provider    Provides access to the results of dependencies declared in [dependencies].
     *                    <br>
     *                    提供对 [dependencies] 中声明的依赖项结果的访问能力。
     * @return The result of the initialization, which will be cached and accessible to other tasks
     *         that depend on this one via [DependenciesProvider].
     *         <br>
     *         初始化的结果。该结果会被缓存，并可以通过 [DependenciesProvider] 提供给其他依赖于此任务的任务使用。
     */
    suspend fun init(application: Application, provider: DependenciesProvider): T

    /**
     * Returns a list of other [Initializer] classes that this task depends on.
     * This task will not start until all dependencies listed here have completed successfully.
     *
     * --- (中文说明) ---
     *
     * 返回此任务所依赖的其他 [Initializer] 类的列表。
     * 在这里列出的所有依赖项成功完成之前，当前任务不会开始执行。
     *
     * @return A list of dependency classes. Defaults to an empty list if there are no dependencies.
     *         <br>
     *         依赖项的类列表。如果没有依赖项，默认为空列表。
     */
    fun dependencies(): List<KClass<out Initializer<*>>> = emptyList()


    /**
     * Declares if this task can be run in a non-main process.
     * Defaults to false, meaning tasks are main-process-only unless overridden.
     * If the current task allows multi-process initialization,
     * then the task it depends on must also support multi-process initialization.
     * ---
     * 声明此任务是否可以在非主进程中运行。
     * 默认为 false，意味着除非被重写，否则任务仅在主进程运行。
     * 如果当前任务允许多进程初始化那么其依赖的任务也必须支持多进程初始化。
     */
    fun isMultiProcess(): Boolean = false
}
