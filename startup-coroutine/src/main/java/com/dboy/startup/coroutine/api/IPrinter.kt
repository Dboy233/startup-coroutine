package com.dboy.startup.coroutine.api

import com.dboy.startup.coroutine.model.TaskMetrics

/**
 * 日志记录器接口。
 * 负责在调试模式下输出诊断信息，如依赖图和性能报告。
 */
interface IPrinter {
    /**
     * 打印依赖拓扑图
     */
    fun printTopologySortingDependGraph(sortedInitializers: List<Initializer<*>>)

    /**
     * 打印耗时
     */
    fun printRunningTimeConsumingSummaries(
        totalDuration: Long,
        taskMetrics: List<TaskMetrics>,
        dispatchersMode: String,
        hasErrors: Boolean
    )
}
