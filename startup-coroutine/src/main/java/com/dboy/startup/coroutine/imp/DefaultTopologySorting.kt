package com.dboy.startup.coroutine.imp

import com.dboy.startup.coroutine.api.ITopologySorting
import com.dboy.startup.coroutine.api.Initializer
import kotlin.reflect.KClass

internal class DefaultTopologySorting() :
    ITopologySorting {

    override fun topologySorting(initializers: List<Initializer<*>>): List<Initializer<*>> {
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

        return sortedList

    }
}