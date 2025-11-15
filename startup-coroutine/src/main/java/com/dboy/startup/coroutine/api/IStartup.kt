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
     * @return 返回一个 Coroutine Job，可用于外部监控或取消。
     */
    fun start(): Job

    /**
     * 以阻塞方式启动所有初始化任务，并等待其全部完成。
     *
     * 此方法会阻塞当前线程，直到所有任务（包括成功和失败的）都执行完毕。
     * 当您需要在应用程序继续执行前确保所有启动任务都已结束时，可以使用此方法。
     * **注意**：请避免在主线程上调用此方法，以免造成应用程序无响应（ANR）。
     *
     * @throws InterruptedException 如果等待过程被中断。
     */
    fun startBlocking()

    /**
     * 取消整个启动流程。
     *
     * 调用此方法会尝试取消所有正在执行或等待执行的任务。
     * 这是一个协作式的取消机制，正在执行的任务需要正确响应取消信号才能被终止。
     */
    fun cancel()
}