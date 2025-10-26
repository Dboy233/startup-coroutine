package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * 一个基于协程的、支持依赖关系、并行化和高级错误处理的异步启动框架。
 *
 * 该框架通过拓扑排序来管理复杂的初始化依赖关系。所有任务默认都在 **主线程** 上启动，
 * 从而可以直接执行UI相关的初始化。开发者可以根据需要在任务内部使用 `withContext(Dispatchers.IO)`
 * 或 `withContext(Dispatchers.Default)` 来执行耗时操作，而不会阻塞启动流程。
 *
 * ### 主要特性
 * - **依赖管理**: 自动处理任务间的依赖关系。
 * - **主线程优先**: 所有任务默认在主线程启动，方便UI操作。
 * - **灵活的线程模型**: 开发者可以轻松地在任务内部切换到后台线程。
 * - **并行执行**: 无依赖关系的任务可以并行执行以缩短启动时间。
 * - **异常隔离**: 使用 `supervisorScope` 确保单个并行任务的失败不会影响其他任务。
 * - **统一错误报告**: 通过 `onError` 回调聚合所有发生的异常。
 * - **可取消**: 可以随时安全地取消整个启动流程。
 *
 * @param context Android Application Context。
 * @param dispatchers 协程调度器配置，用于定义启动、执行和回调的线程模型。
 *                    默认为 `StartupDispatchers.createDefault()`。
 * @param initializers 所有需要执行的 [Initializer] 任务列表。
 * @param onCompletion 所有任务成功执行后的回调，在主线程上调用。
 * @param onError 任何任务执行失败后的回调，聚合所有异常后在主线程上调用。
 *
 * @sample
 * // class AnalyticsInitializer : Initializer<AnalyticsSDK>() { ... }
 * // class AdsInitializer : Initializer<Unit>() { ... }
 *
 * val startup = Startup(
 *     context = applicationContext,
 *     initializers = listOf(AnalyticsInitializer(), AdsInitializer()),
 *     onCompletion = { Log.d("App", "All initializers completed!") },
 *     onError = { errors -> Log.e("App", "Startup failed with ${errors.size} errors.") }
 * )
 * startup.start()
 */
open class Startup(
    private val context: Context,
    private val dispatchers: StartupDispatchers = StartupDispatchers.createDefault(),
    private val initializers: List<Initializer<*>>,
    private val onCompletion: () -> Unit,
    private val onError: ((List<Throwable>) -> Unit)? = null
) : DependenciesProvider {

    // Stores the results of each initializer.
    // 用于存储每个初始化任务的结果。
    private val results = ConcurrentHashMap<KClass<out Initializer<*>>, Any>()

    // CoroutineScope 用于管理所有初始化任务的生命周期。
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.startDispatcher)

    // Atomic flag to prevent multiple invocations of the start() method.
    // 原子状态锁，防止 start() 方法被多次调用。
    private val started = AtomicBoolean(false)

    /**
     * Starts the entire initialization process.
     * This method is thread-safe and can only be successfully invoked once.
     *
     * --- (中文说明) ---
     *
     * 启动整个初始化流程。
     * 此方法是线程安全的，且只能被成功调用一次。
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        // 使用 compareAndSet 确保 start 逻辑只执行一次
        // Use compareAndSet to ensure that the start logic is executed only once
        if (!started.compareAndSet(false, true)) {
            // Already started, return silently or log a warning.
            // 如果已经启动，可以选择静默返回或记录一个警告。
            return
        }

        scope.launch {
            val exceptions = mutableListOf<Throwable>()
            try {
                // 1. Sorts and validates initializers topologically.
                // 1. 对任务进行拓扑排序和验证。
                val sortedInitializers = topologicalSortAndValidate(initializers)

                val serialInitializers =
                    sortedInitializers.filter { it.initMode() == InitMode.SERIAL }
                val parallelInitializers =
                    sortedInitializers.filter { it.initMode() == InitMode.PARALLEL }

                // 2. Execute all serial tasks. A failure here will throw an exception and terminate the process immediately.
                // 2. 执行所有串行任务。如果串行任务失败，将立即抛出异常并终止整个启动流程。
                for (initializer in serialInitializers) {
                    withContext(dispatchers.executeDispatcher) {
                        execute(initializer)
                    }
                }

                // 3. 按依赖关系执行所有并行任务。
                //    使用 supervisorScope 隔离并行任务，一个任务的失败不会取消其他任务。
                supervisorScope {
                    val parallelJobs = mutableMapOf<KClass<out Initializer<*>>, Deferred<*>>()

                    for (initializer in parallelInitializers) {
                        val job = async(dispatchers.executeDispatcher) {
                            // Before starting the current task, wait for its dependencies to complete.
                            // 在启动当前任务前，先等待其所有依赖项完成。
                            val dependencyJobs = initializer.dependencies()
                                .mapNotNull { dependencyClass -> parallelJobs[dependencyClass] }

                            // 等待所有并行的依赖任务完成
                            // 如果任何依赖项失败，awaitAll会抛出异常，此任务也将失败
                            dependencyJobs.awaitAll()

                            // After all dependencies are met, execute the current task.
                            // 所有依赖都完成后，执行当前任务。
                            execute(initializer)
                        }
                        parallelJobs[initializer::class] = job
                    }

                    // Wait for all parallel jobs to complete (successfully or with failure).
                    // 等待所有并行任务完成（无论成功或失败）。
                    parallelJobs.values.awaitAll()
                }

            } catch (e: Throwable) {
                // Catches all exceptions from the startup process, including failures from serial tasks
                // and composite exceptions from parallel tasks.
                // 捕获所有在启动流程中发生的异常，包括串行任务的失败和并行任务的复合异常。
                exceptions.add(e)
                // Collect all suppressed exceptions (from multiple failing jobs in awaitAll).
                // 收集所有被抑制的异常（来自 awaitAll 的多个失败任务）。
                e.suppressed.forEach { exceptions.add(it) }
            } finally {

                // If the scope was cancelled, ensure a CancellationException is reported.
                // 如果 scope 被主动取消，确保一个 CancellationException 被报告。
                if (scope.coroutineContext[Job]?.isCancelled == true && exceptions.none { it is CancellationException }) {
                    // 如果是被取消的，并且异常列表里还没有 CancellationException，就手动添加一个
                    exceptions.add(CancellationException("Startup was cancelled."))
                }

                // Check if any exceptions occurred.
                // 检查是否发生了异常。
                if (exceptions.isNotEmpty()) {
                    // 如果有错误回调，则调用它
                    if (isActive) {
                        withContext(dispatchers.callbackDispatcher) {
                            onError?.invoke(exceptions)
                        }
                    } else {
                        GlobalScope.launch(dispatchers.callbackDispatcher) {
                            onError?.invoke(exceptions)
                        }
                    }
                } else {
                    // If all tasks succeeded, invoke the completion callback.
                    // 4. 所有任务成功完成后，调用完成回调。
                    if (isActive) {
                        withContext(dispatchers.callbackDispatcher) {
                            onCompletion.invoke()
                        }
                    } else {
                        GlobalScope.launch(dispatchers.callbackDispatcher) {
                            onCompletion.invoke()
                        }
                    }
                }
            }
        }
    }

    /**
     * Executes a single initializer and stores its result if it's not Unit.
     * --- (中文说明) ---
     * 执行单个初始化任务并存储其结果（如果结果不是 Unit）。
     */
    private suspend fun execute(initializer: Initializer<*>) {
        val result = initializer.init(context, this)
        if (result !is Unit && result != null) {
            results[initializer::class] = result
        }
    }

    /**
     * Sorts initializers topologically and validates their dependencies.
     * Throws an exception if a circular or illegal dependency is detected.
     * --- (中文说明) ---
     * 对初始化任务进行拓扑排序并验证依赖关系。
     * 如果存在循环依赖或非法依赖，会抛出异常。
     */
    private fun topologicalSortAndValidate(initializers: List<Initializer<*>>): List<Initializer<*>> {
        val sortedList = mutableListOf<Initializer<*>>()
        val inDegree = mutableMapOf<KClass<out Initializer<*>>, Int>()
        val graph =
            mutableMapOf<KClass<out Initializer<*>>, MutableList<KClass<out Initializer<*>>>>()
        val initializerMap = initializers.associateBy { it::class }

        // --- Stage 1: Validation ---
        // --- 阶段一：执行所有验证 ---
        for (initializer in initializers) {
            // 1. 初始化每个节点的图结构和入度
            inDegree[initializer::class] = 0
            graph[initializer::class] = mutableListOf()

            // 2. 遍历其所有依赖项，进行验证
            if (initializer.initMode() == InitMode.SERIAL) {
                for (dependencyClass in initializer.dependencies()) {
                    // 验证 A: 依赖项是否已注册？
                    val dependency = initializerMap[dependencyClass]
                        ?: throw IllegalStateException("Dependency ${dependencyClass.simpleName} for ${initializer::class.simpleName} not found in the initializers list.")

                    // 验证 B: 当前任务是串行时，其依赖项是否也是串行？
                    if (initializer.initMode() == InitMode.SERIAL && dependency.initMode() == InitMode.PARALLEL) {
                        throw IllegalStateException(
                            "Illegal dependency: Serial initializer '${initializer::class.simpleName}' cannot depend on Parallel initializer '${dependency::class.simpleName}'."
                        )
                    }
                }
            }
        }

        // --- Stage 2: Graph Building ---
        // --- 阶段二：构建图并计算入度 ---
        for (initializer in initializers) {
            for (dependency in initializer.dependencies()) {
                graph[dependency]?.add(initializer::class)
                inDegree[initializer::class] = (inDegree[initializer::class] ?: 0) + 1
            }
        }

        // --- Stage 3: Topological Sort ---
        // --- 阶段三：执行拓扑排序 ---
        val queue = ArrayDeque(inDegree.filterValues { it == 0 }.keys)

        while (queue.isNotEmpty()) {
            val clazz = queue.removeFirst()
            initializerMap[clazz]?.let { sortedList.add(it) }

            graph[clazz]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor]!! - 1)
                if (inDegree[neighbor] == 0) {
                    queue.add(neighbor)
                }
            }
        }

        if (sortedList.size != initializers.size) {
            throw IllegalStateException("Circular dependency detected in initializers!")
        }

        return sortedList
    }

    /**
     * 取消所有正在进行的初始化任务。
     */
    fun cancel() {
        scope.cancel("Startup cancelled by caller.")
    }


    /**
     * Retrieves the result of a completed dependency. Throws an exception if the result is unavailable.
     * --- (中文说明) ---
     * 获取一个已完成依赖项的结果。如果结果不可用，则抛出异常。
     */
    override fun <T> result(dependency: KClass<out Initializer<*>>): T {
        return results[dependency] as? T
            ?: throw IllegalStateException("Result for ${dependency.simpleName} not found. Is it declared as a dependency and does it return a non-Unit value?")
    }

    /**
     * Safely retrieves the result of a completed dependency, or `null` if it's unavailable.
     * --- (中文说明) ---
     * 安全地获取一个已完成依赖项的结果，如果结果不可用则返回 `null`。
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> resultOrNull(dependency: KClass<out Initializer<*>>): T? {
        return results[dependency] as? T
    }
}
