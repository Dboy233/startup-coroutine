package com.dboy.startup_coroutine

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
 * @see Startup The actual manager and executor of the tasks.
 * @sample
 * // A parallel task that returns a String result
 * // 一个返回字符串结果的并行任务
 * class MyTask : Initializer<String>() {
 *     override suspend fun init(context: Context, provider: DependenciesProvider): String {
 *         kotlinx.coroutines.delay(1000) // Simulate a long-running operation
 *         return "Task Result"
 *     }
 *
 *     override fun initMode(): InitMode = InitMode.PARALLEL
 * }
 */
abstract class Initializer<T> {

    /**
     * Executes the actual initialization work.
     *
     * This method will be invoked on a CoroutineDispatcher specified by the framework:
     * - For [InitMode.SERIAL] tasks, it runs on the **main thread**.
     * - For [InitMode.PARALLEL] tasks, it runs on a **background thread**.
     *
     * This is a suspend function, allowing you to perform long-running operations. You can also
     * use `withContext` to switch to other dispatchers if needed.
     *
     * --- (中文说明) ---
     *
     * 执行实际的初始化工作。
     *
     * 此方法将在框架指定的协程调度器上被调用：
     * - 对于 [InitMode.SERIAL] 任务，它运行在 **主线程** 上。
     * - 对于 [InitMode.PARALLEL] 任务，它运行在 **后台线程** 上。
     *
     * 这是一个挂起函数，你可以在内部执行耗时操作。如果需要，也可以使用 `withContext` 切换到其他调度器。
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
     * Defines the execution mode for this task.
     *
     * - [InitMode.SERIAL]: The task will be executed sequentially on the main thread. Suitable for UI-related or strictly ordered tasks.
     * - [InitMode.PARALLEL]: The task will be executed concurrently in a background thread pool. Ideal for long-running operations.
     *
     * --- (中文说明) ---
     *
     * 定义此任务的执行模式。
     *
     * - [InitMode.SERIAL]: 任务将在主线程上按顺序执行。适用于需要访问UI或有严格先后顺序的任务。
     * - [InitMode.PARALLEL]: 任务将在后台线程池中与其他并行任务一起执行。适用于不依赖主线程的耗时操作。
     *
     * @return The [InitMode]. Defaults to [InitMode.SERIAL].
     * ([InitMode]。默认为 [InitMode.SERIAL]。)
     */
    open fun initMode(): InitMode = InitMode.SERIAL
}