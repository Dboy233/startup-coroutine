package com.dboy.startup.coroutine.api

import kotlinx.coroutines.Job


/**
 * 定义了启动任务的核心行为。
 */
interface IStartup {
    /**
     * 异步启动所有初始化任务。
     *
     * 此方法会立即返回，并在内部通过协程（`launch`）执行任务。
     * 这是一种非阻塞的启动方式，适用于不希望启动过程阻塞主线程的场景。
     *
     * @return 返回一个 Coroutine Job，可用于外部监控，等待，取消
     */
    fun start(): Job

    /**
     * 取消整个启动流程。
     *
     * 调用此方法会尝试取消所有正在执行或等待执行的任务。
     * 这是一个协作式的取消机制，正在执行的任务需要正确响应取消信号才能被终止。
     */
    fun cancel()
}