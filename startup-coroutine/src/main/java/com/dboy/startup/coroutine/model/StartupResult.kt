package com.dboy.startup.coroutine.model


/**
 * Represents the final result of the startup process.
 * It's a sealed class that can either be [Success] or [Failure].
 * ---
 * 表示启动流程的最终结果。
 * 这是一个密封类，其类型可以是 [Success] 或 [Failure]。
 */
sealed class StartupResult {

    /**
     * Indicates that all initializer tasks completed successfully.
     * ---
     * 表示所有初始化任务均已成功完成。
     */
    object Success : StartupResult()

    /**
     * Indicates that one or more initializer tasks failed during the startup process.
     * ---
     * 表示在启动过程中至少有一个初始化任务失败。
     *
     * @property exceptions A list of [StartupException] objects, each containing details about a failed task and its corresponding error.
     * ---
     * @property exceptions 一个 [StartupException] 列表，其中每个对象都包含了关于失败任务及其对应错误的详细信息。
     */
    data class Failure(val exceptions: List<StartupException>) : StartupResult()
}
