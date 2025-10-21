package com.dboy.startup_coroutine

import android.content.Context


/**
 * 定义一个初始化任务。每个需要初始化的SDK都应该实现这个抽象类。
 * @param T 初始化完成后返回的数据类型。如果不需要返回数据，可以使用 [Unit]。
 */
abstract class Initializer<T> {
    /**
     * 执行实际的初始化工作。
     * 这是一个挂起函数，允许在内部切换线程，例如 withContext(Dispatchers.IO)。
     *
     * @param context Application context.
     * @param dispatcher 一个调度器实例，可以用来获取依赖项的初始化结果。
     * @return 初始化完成后的结果。
     */
    abstract suspend fun init(context: Context, dispatcher: ResultDispatcher): T

    /**
     * 定义此初始化任务的依赖项。
     * @return 一个包含其所依赖的 Initializer class 的列表。默认为空列表。
     */
    open fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    /**
     * 定义此任务的初始化模式（串行或并行）。
     * @return [InitMode]。默认为并行。
     */
    open fun initMode(): InitMode = InitMode.SERIAL
}