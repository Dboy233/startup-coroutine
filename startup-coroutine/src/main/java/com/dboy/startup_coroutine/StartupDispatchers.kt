package com.dboy.startup_coroutine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers


/**
 * **默认方案 (推荐)**
 * - 启动和拓扑排序在 **IO线程** 执行，避免阻塞主线程。
 * - 初始化任务和最终回调在 **主线程** 执行，方便UI操作和结果处理。
 *
 * 这是最常用和最均衡的配置。
 */
val DefaultDispatchers = StartupDispatchers(
    startDispatcher = Dispatchers.IO,
    executeDispatcher = Dispatchers.Main,
    callbackDispatcher = Dispatchers.Main
)

/**
 * **全主线程方案**
 * - 整个启动流程的所有环节（启动、执行、回调）都在 **主线程** 执行。
 * - **注意**: 如果初始化任务中有任何耗时操作，此方案可能导致UI卡顿。
 *   仅适用于所有任务都非常轻量级的场景。
 */
val AllMainDispatchers = StartupDispatchers(
    startDispatcher = Dispatchers.Main,
    executeDispatcher = Dispatchers.Main,
    callbackDispatcher = Dispatchers.Main
)

/**
 * **全IO线程方案**
 * - 整个启动流程的所有环节都在 **IO线程** 执行。
 * - **注意**: 在这种模式下，`Initializer` 内部不能直接进行UI操作。
 *   如果需要更新UI，必须手动 `withContext(Dispatchers.Main)`。
 */
val AllIODispatchers = StartupDispatchers(
    startDispatcher = Dispatchers.IO,
    executeDispatcher = Dispatchers.IO,
    callbackDispatcher = Dispatchers.IO,
)

/**
 * **执行在IO方案**
 * - 启动和初始化任务在 **IO线程** 执行，适合大量IO密集型或CPU密集型任务，避免阻塞UI。
 * - 回调在Main线程执行，确保UI更新。
 * - **注意**: 与 `AllIODispatchers` 方案类似，`Initializer` 内部不能直接操作UI。
 */
val ExecuteOnIODispatchers: StartupDispatchers = StartupDispatchers(
    startDispatcher = Dispatchers.IO,
    executeDispatcher = Dispatchers.IO,
    callbackDispatcher = Dispatchers.Main
)


internal fun getDispatchersMode(dispatcherMode: StartupDispatchers): String {
    return when (dispatcherMode) {
        DefaultDispatchers -> {
            "Default"
        }

        AllMainDispatchers -> {
            "All-Main"
        }

        AllIODispatchers -> {
            "All-IO"
        }

        ExecuteOnIODispatchers -> {
            "Execute-On-IO"
        }

        else -> {
            "Customization-Dispatchers"
        }
    }
}


/**
 * 一个封装了 Startup 框架所需的所有协程调度器的配置类。
 *
 * 这使得线程管理策略可以被轻松地预定义和复用。
 *
 * @property startDispatcher 用于启动顶层协程（即 `Startup.start()` 方法内部的 `scope.launch`）的调度器。
 *                         它决定了拓扑排序等启动前置逻辑在哪个线程上执行。
 * @property executeDispatcher 用于执行每一个 `Initializer` 任务（`init` 方法）的调度器。
 *                           这是任务实际运行所在的线程上下文。
 * @property callbackDispatcher 用于执行最终的 `onCompletion` 和 `onError` 回调的调度器。
 *                            通常应设置为 `Dispatchers.Main` 以确保UI安全。
 *
 * @see
 */
data class StartupDispatchers(
    val startDispatcher: CoroutineDispatcher,
    val executeDispatcher: CoroutineDispatcher,
    val callbackDispatcher: CoroutineDispatcher
)

