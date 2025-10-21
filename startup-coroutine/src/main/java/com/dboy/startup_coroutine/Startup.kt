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

/**
 * 启动和管理所有初始化任务的核心类。
 * 经过优化，增强了异常处理和执行稳定性。
 */
class Startup(
    private val context: Context,
    private val initializers: List<Initializer<*>>,
    private val onCompletion: () -> Unit,
    private val onError: ((List<Throwable>) -> Unit)? = null // 新增：用于报告所有异常的统一回调
) : ResultDispatcher {
    // 用于存储每个初始化任务的结果
    private val results = ConcurrentHashMap<Class<out Initializer<*>>, Any>()

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
                    val parallelJobs = mutableMapOf<Class<out Initializer<*>>, Deferred<*>>()

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
                        parallelJobs[initializer.javaClass] = job
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
        // 使用 Any? 来处理 Unit 返回类型
        (result as? Any)?.let {
            results[initializer.javaClass] = it
        }
    }

    /**
     * 对初始化任务进行拓扑排序并验证依赖关系。
     * 合并了验证逻辑以提高效率。如果存在循环依赖或非法依赖，会抛出异常。
     */
    private fun topologicalSortAndValidate(initializers: List<Initializer<*>>): List<Initializer<*>> {
        val sortedList = mutableListOf<Initializer<*>>()
        val inDegree = mutableMapOf<Class<out Initializer<*>>, Int>()
        val graph = mutableMapOf<Class<out Initializer<*>>, MutableList<Class<out Initializer<*>>>>()
        val initializerMap = initializers.associateBy { it.javaClass }

        // 初始化入度和图，并在此处进行依赖验证
        for (initializer in initializers) {
            val clazz = initializer.javaClass
            inDegree[clazz] = 0
            graph[clazz] = mutableListOf()

            // **验证逻辑**: 串行任务不能依赖并行任务
            if (initializer.initMode() == InitMode.SERIAL) {
                for (dependencyClass in initializer.dependencies()) {
                    val dependency = initializerMap[dependencyClass]
                        ?: throw IllegalStateException("Dependency ${dependencyClass.simpleName} for ${initializer.javaClass.simpleName} not found.")

                    if (dependency.initMode() == InitMode.PARALLEL) {
                        throw IllegalStateException(
                            "Illegal dependency: Serial initializer '${initializer.javaClass.simpleName}' cannot depend on Parallel initializer '${dependency.javaClass.simpleName}'."
                        )
                    }
                }
            }
        }

        // 构建图和计算入度
        for (initializer in initializers) {
            for (dependency in initializer.dependencies()) {
                if (!initializerMap.containsKey(dependency)) {
                    throw IllegalStateException("${initializer.javaClass.simpleName} depends on ${dependency.simpleName}, which is not in the initializers list.")
                }
                graph[dependency]?.add(initializer.javaClass)
                inDegree[initializer.javaClass] = (inDegree[initializer.javaClass] ?: 0) + 1
            }
        }

        // 拓扑排序核心算法 (Kahn's algorithm)
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
    @Suppress("UNCHECKED_CAST")
    override fun <T> getResult(dependency: Class<out Initializer<T>>): T {
        // 由于拓扑排序和执行流程保证了依赖项已经执行完毕，这里可以安全地获取结果。
        return results[dependency] as? T
            ?: throw IllegalStateException("Result for ${dependency.simpleName} not found. Is it declared as a dependency and does it return a non-Unit value?")
    }
}
