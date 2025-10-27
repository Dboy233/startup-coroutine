package com.dboy.startup.coroutine.api

import android.content.Context
import kotlin.reflect.KClass

/**
 * The core abstraction for an initialization task.
 *
 * Every unit of work that needs to be managed (e.g., SDK initialization, database loading)
 * should extend this class. It defines the task's execution logic, dependencies, and execution mode.
 *
 * --- (中文说明) ---
 *
 * 定义一个初始化任务的核心抽象。
 *
 * 每一个需要被管理的初始化单元（如SDK初始化、数据库加载等）都应该继承这个类。
 * 它定义了任务的执行逻辑、依赖关系和执行模式。
 *
 * @param T The data type returned after initialization. Use [Unit] if no result is needed.
 * (初始化完成后返回的数据类型。如果任务不需要返回任何结果，请使用 [Unit]。)
 *
 * @see com.dboy.startup.coroutine.Startup The actual manager and executor of the tasks.
 * @sample
 * // A parallel task that returns a String result and performs I/O
 * // 一个返回字符串结果并执行I/O操作的并行任务
 * class MyTask :Initializer<String>() {
 *     override suspend fun init(context: Context, provider: DependenciesProvider): String {
 *         // Perform long-running I/O operation on a background thread
 *         val data = withContext(Dispatchers.IO) {
 *             // Simulate file reading or network call
 *             delay(1000)
 *             "Task Result"
 *         }
 *         return data
 *     }
 *
 *     override fun initMode(): InitMode = InitMode.PARALLEL
 * }
 */
abstract class Initializer<T> {


    /**
     * Executes the actual initialization work.
     *
     * This is a suspend function that has nothing to do with [InitMode],
     * it is related to the thread context set by [com.dboy.startup.coroutine.StartupDispatchers.executeDispatcher].
     *
     * **Important**: For any potentially blocking or long-running operations (e.g., file I/O,
     * network requests, heavy computation), you **must** switch to a background dispatcher
     * using `withContext(Dispatchers.IO)` or `withContext(Dispatchers.Default)` to avoid
     * blocking the main thread and causing ANRs.
     *
     * --- (中文说明) ---
     *
     * 执行实际的初始化工作。
     *
     * 这是一个挂起函数，所在线程与 [InitMode] 无关，
     * 它与[com.dboy.startup.coroutine.StartupDispatchers.executeDispatcher]设置的线程上下文有关。
     *
     * **重要提示**: 对于任何潜在的阻塞或长时间运行的操作（例如：文件I/O、网络请求、复杂计算），
     * 你 **必须** 使用 `withContext(Dispatchers.IO)` 或 `withContext(Dispatchers.Default)`
     * 将其切换到后台调度器上执行.
     *
     * @param context The application's global context, whose lifecycle is tied to the app process.
     * (Application全局上下文，生命周期与应用进程一致。)
     * @param provider A dependency provider used to get the results of dependent tasks.
     * (一个依赖提供者，用于在当前任务中获取其依赖项的执行结果。)
     * @return The result after initialization is complete. Return [Unit] if no result is needed.
     * (初始化完成后的结果。如果不需要结果，返回 [Unit]。)
     * @see DependenciesProvider.result
     * @see DependenciesProvider.resultOrNull
     */
    abstract suspend fun init(context: Context, provider: DependenciesProvider): T

    /**
     * Defines the other tasks that this initializer depends on.
     *
     * The framework ensures that all dependencies declared in this list are completed
     * before this task is executed.
     *
     * --- (中文说明) ---
     *
     * 定义此初始化任务所依赖的其他任务。
     *
     * 框架会确保所有在此列表中声明的依赖任务都执行完毕后，才会执行当前任务。
     *
     * @return A list of [KClass] for the [Initializer]s it depends on. Returns an empty list if there are no dependencies.
     * (一个由其依赖的 [Initializer] 的 [KClass] 组成的列表。如果无依赖，则返回空列表。)
     * @sample
     * override fun dependencies() = listOf(TaskA::class, TaskB::class)
     */
    open fun dependencies(): List<KClass<out Initializer<*>>> = emptyList()


    /**
     * Defines the execution mode for this task, which primarily controls execution order.
     *
     * - [InitMode.SERIAL]: The task will be executed sequentially, respecting its dependencies.
     * - [InitMode.PARALLEL]: The task can be executed concurrently with other parallel tasks once its dependencies are met.
     *
     * Note that the execution mode **does not** determine the execution thread. All tasks
     * start on the main thread.
     *
     * --- (中文说明) ---
     * 定义此任务的执行模式，它主要控制执行顺序。
     * - [InitMode.SERIAL] : 任务将按其依赖关系顺序执行。
     * - [InitMode.PARALLEL] : 任务在依赖满足后，可以与其他并行任务并发执行。
     *
     * 请注意，执行模式 **不决定** 执行线程。
     *
     * @return The [InitMode]. Defaults to [InitMode.SERIAL].
     * ([InitMode]。默认为 [InitMode.SERIAL]。)
     */
    open fun initMode(): InitMode = InitMode.SERIAL
}