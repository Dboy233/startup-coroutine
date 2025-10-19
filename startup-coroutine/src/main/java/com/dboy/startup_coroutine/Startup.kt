package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 启动和管理所有初始化任务的核心类。
 */
class Startup(
    private val context: Context,
    private val initializers: List<Initializer<*>>,
    private val onCompletion: () -> Unit
) : ResultDispatcher {
    // 用于存储每个初始化任务的结果
    private val results = ConcurrentHashMap<Class<out Initializer<*>>, Any>()

    // CoroutineScope 来管理所有初始化任务的生命周期
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 创建一个单线程的 Executor 代替 newSingleThreadContext("Serial-Initializer-Thread")
    // 使用单一线程的协程上下文来确保串行执行
    private val serialContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /**
     * 启动整个初始化流程。
     */
    fun start() {
        scope.launch {
            try {
                validateDependencies(initializers)
                // 拓扑排序确保了执行顺序
                val sortedInitializers = topologicalSort(initializers)

                val serialInitializers =
                    sortedInitializers.filter { it.initMode() == InitMode.SERIAL }
                val parallelInitializers =
                    sortedInitializers.filter { it.initMode() == InitMode.PARALLEL }

                // 1. 执行所有串行任务
                for (initializer in serialInitializers) {
                    // 在串行线程上执行，但允许其内部切换
                    withContext(serialContext) {
                        execute(initializer)
                    }
                }

//                // 2. 并发执行所有并行任务
//                // Await all parallel jobs to complete
//                parallelInitializers.map { initializer ->
//                    async(Dispatchers.Default) { // 使用 async 以便可以 await
//                        execute(initializer)
//                    }
//                }.awaitAll() // 等待所有并行任务完成

                // 2. 按依赖关系执行所有并行任务
                // 创建一个 Map 来追踪每个并行任务的 Deferred/Job
                val parallelJobs = mutableMapOf<Class<out Initializer<*>>, Deferred<*>>()

                for (initializer in parallelInitializers) {
                    val job = async(Dispatchers.Default) {
                        // 在启动当前任务前，先等待其所有依赖项完成
                        val dependencyJobs = initializer.dependencies()
                            .mapNotNull { dependencyClass ->
                                // 依赖项可能是另一个并行任务，从 map 中找到它的 job
                                parallelJobs[dependencyClass]
                                // 依赖项也可能是串行任务，它们已经完成了，所以 parallelJobs[dependencyClass] 会是 null
                            }

                        // 等待所有依赖的并行任务完成
                        dependencyJobs.awaitAll()

                        // 所有依赖都完成后，执行当前任务
                        execute(initializer)
                    }
                    parallelJobs[initializer.javaClass] = job
                }

                // 等待所有并行任务的根节点完成
                parallelJobs.values.awaitAll()

                // 3. 所有任务完成后，在主线程上调用回调
                withContext(Dispatchers.Main) {
                    onCompletion.invoke()
                }
            } finally {
                serialContext.close()
            }
        }
    }

    /**
     * 执行单个初始化任务并存储其结果。
     * 注意：现在它将 `this` (作为 Dispatcher) 传递给了 init 方法。
     */
    private suspend fun execute(initializer: Initializer<*>) {
        // 调用更新后的 init 方法，将 dispatcher 实例传递进去
        val result = initializer.init(context, this) // <--- 关键改动
        results[initializer.javaClass] = result as Any
    }

    /**
     * 验证所有初始化任务的依赖关系是否合法。
     * 主要检查：
     * 1. 串行任务不能依赖并行任务。
     * （循环依赖将在拓扑排序中被检测）
     */
    private fun validateDependencies(initializers: List<Initializer<*>>) {
        val initializerMap = initializers.associateBy { it.javaClass }

        for (initializer in initializers) {
            // 检查：如果当前任务是串行的
            if (initializer.initMode() == InitMode.SERIAL) {
                // 遍历它的所有依赖
                for (dependencyClass in initializer.dependencies()) {
                    val dependency = initializerMap[dependencyClass]
                        ?: throw IllegalStateException("Dependency ${dependencyClass.simpleName} for ${initializer.javaClass.simpleName} not found in the initializers list.")

                    // 检查：如果依赖项是并行的，则抛出异常
                    if (dependency.initMode() == InitMode.PARALLEL) {
                        throw IllegalStateException(
                            "Illegal dependency: Serial initializer '${initializer.javaClass.simpleName}' cannot depend on Parallel initializer '${dependency.javaClass.simpleName}'."
                        )
                    }
                }
            }
        }
    }

    /**
     * 对初始化任务列表进行拓扑排序，以确保依赖关系正确。
     * 如果存在循环依赖，会抛出异常。
     */
    private fun topologicalSort(initializers: List<Initializer<*>>): List<Initializer<*>> {
        val sortedList = mutableListOf<Initializer<*>>()
        val inDegree = mutableMapOf<Class<out Initializer<*>>, Int>()
        val graph =
            mutableMapOf<Class<out Initializer<*>>, MutableList<Class<out Initializer<*>>>>()
        val initializerMap = initializers.associateBy { it.javaClass }

        for (initializer in initializers) {
            val clazz = initializer.javaClass
            inDegree[clazz] = 0
            graph[clazz] = mutableListOf()
        }

        for (initializer in initializers) {
            for (dependency in initializer.dependencies()) {
                // 确保依赖存在
                if (!initializerMap.containsKey(dependency)) {
                    throw IllegalStateException("${initializer.javaClass.simpleName} depends on ${dependency.simpleName}, which is not in the initializers list.")
                }
                graph[dependency]?.add(initializer.javaClass)
                inDegree[initializer.javaClass] = (inDegree[initializer.javaClass] ?: 0) + 1
            }
        }

        val queue = ArrayDeque<Class<out Initializer<*>>>()
        inDegree.forEach { (clazz, degree) ->
            if (degree == 0) {
                queue.add(clazz)
            }
        }

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
        serialContext.close()
        scope.cancel()
    }


    /**
     * 根据 Class 类型安全地获取已完成的依赖项的结果。
     * 这是 Dispatcher 接口的实现。
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T> getResult(dependency: Class<out Initializer<T>>): T {
        // 由于拓扑排序保证了依赖项已经执行完毕，这里可以安全地获取结果。
        // 如果结果不存在，说明依赖关系配置有误或逻辑错误，直接抛出异常。
        return results[dependency] as? T
            ?: throw IllegalStateException("Result for ${dependency.simpleName} not found. Is it declared as a dependency?")
    }
}