package com.dboy.startup.coroutine.api

/**
 * 依赖分析器接口。
 * 负责对初始化任务列表进行拓扑排序和依赖验证。
 */
interface ITopologySorting {
    /**
     * 对给定的初始化任务列表进行分析和排序。
     * @param initializers 原始的任务列表。
     * @return 经过拓扑排序后的任务列表。
     * @throws IllegalStateException 如果发现依赖问题（如循环依赖、依赖未注册）。
     */
    fun topologySorting(initializers: List<Initializer<*>>): List<Initializer<*>>
}