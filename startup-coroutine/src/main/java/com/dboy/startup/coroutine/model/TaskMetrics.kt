package com.dboy.startup.coroutine.model

/**
 * 用于保存单个初始值设定项任务的性能指标的数据类。
 */
internal data class TaskMetrics(
    val name: String,
    val duration: Long, val threadName: String
)