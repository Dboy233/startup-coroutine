package com.dboy.startup.coroutine.api

/**
 * The public contract for the Startup Coroutine framework.
 * Defines the essential operations for controlling the startup process.
 *
 * --- (中文说明) ---
 *
 * Startup Coroutine 框架的公共契约。
 * 定义了控制启动流程的核心操作。
 */
interface IStartup {
    /**
     * Starts the entire initialization process.
     */
    fun start()

    /**
     * Cancels all ongoing initialization tasks.
     */
    fun cancel()
}
