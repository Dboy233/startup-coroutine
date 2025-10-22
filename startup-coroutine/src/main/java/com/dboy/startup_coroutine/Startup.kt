package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * 一个基于协程的、支持依赖关系、并行化和高级错误处理的异步启动框架。
 *
 * 该框架通过拓扑排序来管理复杂的初始化依赖关系，并允许任务在主线程（串行）
 * 或后台线程（并行）上执行。它能确保所有任务按正确顺序执行，并在所有任务
 * 完成或发生错误后提供统一的回调。
 *
 * ### 主要特性
 * - **依赖管理**: 自动处理任务间的依赖关系。
 * - **并行执行**: 无依赖关系的任务可以并行执行以缩短启动时间。
 * - **异常隔离**: 使用 `supervisorScope` 确保单个并行任务的失败不会影响其他任务。
 * - **统一错误报告**: 通过 `onError` 回调聚合所有发生的异常。
 * - **可取消**: 可以随时安全地取消整个启动流程。
 *
 * @param context Android Application Context。
 * @param initializers 所有需要执行的 [Initializer] 任务列表。
 * @param onCompletion 所有任务成功执行后的回调，在主线程上调用。
 * @param onError 任何任务执行失败后的回调，在一个后台线程上调用。
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
class Startup(
    private val context: Context,
    private val initializers: List<Initializer<*>>,
    private val onCompletion: () -> Unit,
    private val onError: ((List<Throwable>) -> Unit)? = null // 新增：用于报告所有异常的统一回调
) : DependenciesProvider {
    // 用于存储每个初始化任务的结果
    private val results = ConcurrentHashMap<KClass<out Initializer<*>>, Any>()

    // CoroutineScope 来管理所有初始化任务的生命周期
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 使用单一线程的协程上下文来确保串行执行
    private val serialContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // 原子状态锁，防止 start() 方法被多次调用
    private val started = AtomicBoolean(false)

    /**
     * 启动整个初始化流程。
     * 此方法是线程安全的，且只能被成功调用一次。
     */
    fun start() {
        // 使用 compareAndSet 确保 start 逻辑只执行一次
        if (!started.compareAndSet(false, true)) {
            // 如果已经启动，可以选择静默返回或记录一个警告
            return
        }

        scope.launch {
            val exceptions = mutableListOf<Throwable>()
            try {
                // 1. 合并了验证和拓扑排序，减少遍历次数
                val sortedInitializers = topologicalSortAndValidate(initializers)

                val serialInitializers =
                    sortedInitializers.filter { it.initMode() == InitMode.SERIAL }
                val parallelInitializers =
                    sortedInitializers.filter { it.initMode() == InitMode.PARALLEL }

                // 2. 执行所有串行任务
                // 如果串行任务失败，将立即抛出异常并终止整个启动流程
                for (initializer in serialInitializers) {
                    withContext(serialContext) {
                        execute(initializer)
                    }
                }

                // 3. 按依赖关系执行所有并行任务
                // 使用 supervisorScope 隔离并行任务，一个任务的失败不会取消其他任务
                supervisorScope {
                    val parallelJobs = mutableMapOf<KClass<out Initializer<*>>, Deferred<*>>()

                    for (initializer in parallelInitializers) {
                        val job = async(Dispatchers.Default) {
                            // 在启动当前任务前，先等待其所有依赖项完成
                            val dependencyJobs = initializer.dependencies()
                                .mapNotNull { dependencyClass -> parallelJobs[dependencyClass] }

                            // 等待所有并行的依赖任务完成
                            // 如果任何依赖项失败，awaitAll会抛出异常，此任务也将失败
                            dependencyJobs.awaitAll()

                            // 所有依赖都完成后，执行当前任务
                            execute(initializer)
                        }
                        parallelJobs[initializer::class] = job
                    }

                    // 等待所有并行任务完成（无论成功或失败）。
                    // awaitAll 在 supervisorScope 下，会等待所有 job 结束，
                    // 然后抛出一个包含所有子任务异常的复合异常。
                    parallelJobs.values.awaitAll()
                }

            } catch (e: Throwable) {
                // 捕获所有在启动流程中发生的异常
                // 包括串行任务的失败和并行任务的复合异常
                exceptions.add(e)
                // 收集所有被抑制的异常（来自 awaitAll 的多个失败任务）
                e.suppressed.forEach { exceptions.add(it) }
            } finally {
                // 确保单线程上下文被关闭
                serialContext.close()


                // **关键修改**：检查 scope 是否被主动取消
                if (scope.coroutineContext[Job]?.isCancelled == true && exceptions.none { it is CancellationException }) {
                    // 如果是被取消的，并且异常列表里还没有 CancellationException，就手动添加一个
                    exceptions.add(CancellationException("Startup was cancelled."))
                }

                // 检查是否发生了异常
                if (exceptions.isNotEmpty()) {
                    // 如果有错误回调，则调用它
                    onError?.invoke(exceptions)
                } else {
                    // 4. 所有任务成功完成后，在主线程上调用完成回调
                    withContext(Dispatchers.Main) {
                        onCompletion.invoke()
                    }
                }
            }
        }
    }

    /**
     * 执行单个初始化任务并存储其结果。
     */
    private suspend fun execute(initializer: Initializer<*>) {
        val result = initializer.init(context, this)
        // 只有当结果不是 Unit 时，才将其存入 results Map
        if (result !is Unit && result != null) {
            results[initializer::class] = result
        }
    }

    /**
     * 对初始化任务进行拓扑排序并验证依赖关系。
     * 合并了验证逻辑以提高效率。如果存在循环依赖或非法依赖，会抛出异常。
     */
    private fun topologicalSortAndValidate(initializers: List<Initializer<*>>): List<Initializer<*>> {
        val sortedList = mutableListOf<Initializer<*>>()
        val inDegree = mutableMapOf<KClass<out Initializer<*>>, Int>()
        val graph =
            mutableMapOf<KClass<out Initializer<*>>, MutableList<KClass<out Initializer<*>>>>()
        val initializerMap = initializers.associateBy { it::class }

        // =========================================================
        // 阶段一：执行所有验证 (Validation)
        // =========================================================
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

        // =========================================================
        // 阶段二：构建图并计算入度 (Graph Building)
        // =========================================================
        for (initializer in initializers) {
            for (dependency in initializer.dependencies()) {
                graph[dependency]?.add(initializer::class)
                inDegree[initializer::class] = (inDegree[initializer::class] ?: 0) + 1
            }
        }

        // =========================================================
        // 阶段三：执行拓扑排序 (Topological Sort)
        // =========================================================
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
     * 如果启动流程还未开始，则直接取消 scope；如果已开始，则会中断正在执行的任务。
     */
    fun cancel() {
        if (started.get()) {
            // 如果已经启动，关闭 serialContext 并取消整个 scope
            serialContext.close() // 确保即使协程被取消，线程池也能关闭
        }
        scope.cancel("Startup cancelled by caller.")
    }


    /**
     * 根据 Class 类型安全地获取已完成的依赖项的结果。
     */
    override fun <T> result(dependency: KClass<out Initializer<*>>): T {
        // 由于拓扑排序和执行流程保证了依赖项已经执行完毕，这里可以安全地获取结果。
        return resultOrNull(dependency)
            ?: throw IllegalStateException("Result for ${dependency.simpleName} not found. Is it declared as a dependency and does it return a non-Unit value?")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> resultOrNull(dependency: KClass<out Initializer<*>>): T? {
        // 这里的类型转换在逻辑上是安全的，因为：
        // 1. `execute()` 方法是唯一写入 results 的地方。
        // 2. `execute()` 方法保证了存入的 key (initializer.javaClass) 和 value (result)
        //    的泛型类型 T 是匹配的。
        // 因此，我们抑制了“未经检查的转换”警告。
        return results[dependency] as? T
    }
}
