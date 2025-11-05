package com.dboy.startup.coroutine

import android.util.Log
import com.dboy.startup.coroutine.api.Initializer
import com.dboy.startup.coroutine.inter.IDependencyGraph
import kotlin.reflect.KClass

class DependencyGraphManager(
    private val initializers: List<Initializer<*>>,
    private val isDebug: Boolean
) : IDependencyGraph {

    /**
     * Sorts initializers topologically and validates their dependencies.
     * Throws an exception if a circular or illegal dependency is detected.
     * --- (中文说明) ---
     * 对初始化任务进行拓扑排序并验证依赖关系。
     * 如果存在循环依赖或非法依赖，会抛出异常。
     */
    override fun sortAndValidate(): List<Initializer<*>> {
        val sortedList = mutableListOf<Initializer<*>>()
        val inDegree = mutableMapOf<KClass<out Initializer<*>>, Int>()
        val graph =
            mutableMapOf<KClass<out Initializer<*>>, MutableList<KClass<out Initializer<*>>>>()
        val initializerMap = initializers.associateBy { it::class }

        // --- Stage 1: Validation ---
        // --- 阶段一：创建节点,交验依赖是否存在---
        for (initializer in initializers) {
            // 1. 初始化每个节点的图结构和入度
            inDegree[initializer::class] = 0
            graph[initializer::class] = mutableListOf()

            // 2. 遍历其所有依赖项，进行验证
            for (dependencyClass in initializer.dependencies()) {
                // 验证 A: 依赖项是否已注册？
                initializerMap[dependencyClass]
                    ?: throw IllegalStateException("Dependency ${dependencyClass.simpleName} for ${initializer::class.simpleName} not found in the initializers list.")
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

        // --- Stage 4: Print Dependency Graph ---
        // --- 阶段四：打印依赖关系图 ---
        if (isDebug) {
            printDependenciesGraph(sortedList)
        }

        return sortedList
    }


    /**
     * 打印所有任务的拓扑依赖图到控制台。
     * 格式:
     * ```txt
     * TaskName
     *   ├─ Dependency1
     *   └─ Dependency2
     * ```
     */
    private fun printDependenciesGraph(
        sortedInitializers: List<Initializer<*>>,
    ) {
        val logContent = StringBuilder("\n--- Startup Coroutine Dependency Graph ---\n\n")

        sortedInitializers.forEach { initializer ->
            logContent.append("${initializer::class.simpleName}\n")

            val dependencies = initializer.dependencies()
            if (dependencies.isNotEmpty()) {
                dependencies.forEachIndexed { index, depClass ->
                    val prefix = if (index == dependencies.size - 1) "  └─ " else "  ├─ "
                    logContent.append("$prefix${depClass.simpleName}\n")
                }
            }
        }
        logContent.append("\n----------------------------------------")
        // 使用 Log.d 打印，以便在 Android Logcat 中查看
        Log.d("StartupCoroutine", logContent.toString())
    }


}