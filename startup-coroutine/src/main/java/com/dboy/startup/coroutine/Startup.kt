package com.dboy.startup.coroutine

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.dboy.startup.coroutine.api.IStartup
import com.dboy.startup.coroutine.api.Initializer
import com.dboy.startup.coroutine.imp.StartupImpl
import com.dboy.startup.coroutine.model.StartupResult
import kotlinx.coroutines.Job

/**
 * A high-level, coroutine-based startup framework that supports dependency management and parallel execution.
 *
 * This class serves as the entry point for the initialization process. It orchestrates a list of [Initializer] tasks,
 * managing their dependencies and execution threads according to a topological sort order.
 *
 * ### Key Features
 * - **Dependency Management**: Automatically resolves dependencies between tasks using topological sorting.
 * - **Parallel Execution**: Tasks with no dependencies or whose dependencies are met can run in parallel.
 * - **Thread Scheduling**: Configurable threading models via [StartupDispatchers] (e.g., default, all-main, all-IO).
 * - **Lifecycle Aware**: Provides [StartupResult] observation via LiveData, making it easy to integrate with Activities/Fragments.
 *
 * ### Usage Example
 * ```kotlin
 * val startup = Startup.Builder(application)
 *     .add(LoggerInitializer())
 *     .add(NetworkInitializer()) // Depends on Logger
 *     .setDebug(true)
 *     .build()
 *
 * startup.start()
 *
 * // Observe results
 * Startup.observe(this) { result ->
 *     if (result is StartupResult.Success) { ... }
 * }
 * ```
 *
 * --- (中文说明) ---
 *
 * 一个基于协程的、支持依赖关系管理和并行化执行的高级启动框架。
 *
 * 该类是初始化流程的统一入口。它负责编排一系列 [Initializer] 任务，根据拓扑排序的结果
 * 管理它们的依赖关系和执行线程。
 *
 * ### 核心特性
 * - **依赖管理**: 使用拓扑排序自动解决任务间的依赖关系。
 * - **并行执行**: 没有依赖关系或依赖已满足的任务可以并行运行，充分利用系统资源。
 * - **线程调度**: 通过 [StartupDispatchers] 提供灵活的线程模型配置（如默认策略、全主线程、全IO线程等）。
 * - **生命周期感知**: 通过 LiveData 提供 [StartupResult] 的观察能力，便于与 Activity/Fragment 集成。
 *
 * ### 使用示例
 * ```kotlin
 * val startup = Startup.Builder(application)
 *     .add(LoggerInitializer())
 *     .add(NetworkInitializer()) // 假设依赖于 Logger
 *     .setDebug(true)
 *     .build()
 *
 * startup.start()
 *
 * // 观察结果
 * Startup.observe(this) { result ->
 *     if (result is StartupResult.Success) { ... }
 * }
 * ```
 */
@Suppress("unused")
open class Startup private constructor(
    private val implementation: IStartup
) : IStartup {
    companion object {

        private val _isInitialized = MutableLiveData<StartupResult>(StartupResult.Idle)

        /**
         * Registers an observer to monitor the final result of the startup process.
         *
         * --- (中文说明) ---
         *
         * 注册观察者以监听启动任务的最终结果。
         *
         * @param owner LifecycleOwner (e.g., Activity or Fragment).
         *              <br> LifecycleOwner (如 Activity 或 Fragment)。
         * @param observer The callback to receive [StartupResult].
         *                 <br> 接收 [StartupResult] 的回调。
         */
        fun observe(owner: LifecycleOwner, observer: Observer<StartupResult>) {
            _isInitialized.observe(owner, observer)
        }


        /**
         * Checks if the startup process has finished (i.e., the state is not Idle).
         *
         * As long as the state is not [StartupResult.Idle], whether it is Success or Failure,
         * it is considered initialized.
         *
         * --- (中文说明) ---
         *
         * 检查启动流程是否已经结束（非空闲状态）。
         *
         * 只要状态不是 [StartupResult.Idle]，无论是成功还是失败，都视为已初始化。
         *
         * @return True if initialized, false otherwise.
         *         <br> 如果已初始化返回 true，否则返回 false。
         */
        fun isInitialized() = _isInitialized.value != StartupResult.Idle

        /**
         * Retrieves the current result object of the startup process.
         *
         * --- (中文说明) ---
         *
         * 获取当前的启动结果对象。
         *
         * @return The current [StartupResult] state.
         *         <br> 当前的 [StartupResult] 状态。
         */
        fun initializedResult() = _isInitialized.value

        /**
         * Resets the startup state to [StartupResult.Idle].
         *
         * Typically used in testing environments or scenarios where the full startup process
         * needs to be re-executed.
         *
         * --- (中文说明) ---
         *
         * 重置启动状态为 [StartupResult.Idle]（空闲）。
         *
         * 通常用于测试环境或需要重新执行完整启动流程的场景。
         */
        fun reset() {
            _isInitialized.postValue(StartupResult.Idle)
        }

        /**
         * (Internal use) Updates the global initialization result state.
         *
         * --- (中文说明) ---
         *
         * (内部使用) 更新全局的初始化结果状态。
         *
         * @param result The new state result.
         *               <br> 新的状态结果。
         */
        internal fun markInitializedResult(result: StartupResult) {
            _isInitialized.postValue(result)
        }
    }


    override fun start(): Job {
        return implementation.start()
    }

    override fun cancel() {
        implementation.cancel()
    }

    /**
     * Builder class for configuring and creating a [Startup] instance.
     *
     * --- (中文说明) ---
     *
     * 建造者类，用于配置和创建 [Startup] 实例。
     */
    class Builder(private val application: Application) {
        private var isDebug: Boolean = false
        private var dispatchers: StartupDispatchers = StartupDispatchers.Default
        private val initializers: MutableList<Initializer<*>> = mutableListOf()

        @Deprecated(
            message = "use `Startup.observe` subscribe to the results",
            replaceWith = ReplaceWith("Startup.observe"),
        )
        private var onResult: ((StartupResult) -> Unit)? = null

        /**
         * Sets whether to enable debug mode.
         *
         * When enabled, detailed task dependency graphs and execution time statistics
         * will be output to the console.
         *
         * --- (中文说明) ---
         *
         * 设置是否开启调试模式。
         *
         * 开启后，控制台会输出详细的任务依赖图和耗时统计信息。
         *
         * @param isDebug true to enable debug, false to disable.
         *                <br> true 开启调试，false 关闭。
         */
        fun setDebug(isDebug: Boolean): Builder =
            apply { this.isDebug = isDebug }

        /**
         * Sets the coroutine dispatcher configuration.
         *
         * Used to control the threads for the startup process and task execution
         * (e.g., all-main thread, all-IO thread, etc.).
         *
         * --- (中文说明) ---
         *
         * 设置协程调度器配置。
         *
         * 用于控制启动流程和任务执行所在的线程（如全主线程、全IO线程等）。
         *
         * @param dispatchers The [StartupDispatchers] configuration object.
         *                    <br> [StartupDispatchers] 配置对象。
         */
        fun setDispatchers(dispatchers: StartupDispatchers): Builder =
            apply { this.dispatchers = dispatchers }

        /**
         * Adds a single initialization task.
         *
         * --- (中文说明) ---
         *
         * 添加一个初始化任务。
         *
         * @param initializer An instance implementing the [Initializer] interface.
         *                    <br> 实现 [Initializer] 接口的任务实例。
         */
        fun add(initializer: Initializer<*>): Builder =
            apply { this.initializers.add(initializer) }

        /**
         * Adds a list of initialization tasks in batch.
         *
         * --- (中文说明) ---
         *
         * 批量添加初始化任务列表。
         *
         * @param initializers The list of initialization tasks.
         *                     <br> 初始化任务列表。
         */
        fun add(initializers: List<Initializer<*>>): Builder =
            apply { this.initializers.addAll(initializers) }

        /**
         * Adds initialization tasks in batch (varargs).
         *
         * --- (中文说明) ---
         *
         * 批量添加初始化任务（可变参数）。
         *
         * @param initializers An array of initialization tasks.
         *                     <br> 初始化任务数组。
         */
        fun add(vararg initializers: Initializer<*>): Builder =
            apply { this.initializers.addAll(initializers) }

        @Deprecated(
            message = "use `Startup.observe` subscribe to the results",
            replaceWith = ReplaceWith("Startup.observe"),
        )
        fun setOnResult(onResult: (StartupResult) -> Unit): Builder =
            apply { this.onResult = onResult }

        /**
         * Builds a [Startup] instance based on the current configuration.
         *
         * --- (中文说明) ---
         *
         * 根据当前配置构建一个 [Startup] 实例。
         *
         * @return The configured Startup instance.
         *         <br> 配置完成的 Startup 实例。
         */
        fun build(): Startup {
            val impl = StartupImpl(
                application = application,
                isDebug = isDebug,
                dispatchers = dispatchers,
                initializers = initializers,
                onResult = onResult
            )
            return Startup(impl)
        }
    }
}