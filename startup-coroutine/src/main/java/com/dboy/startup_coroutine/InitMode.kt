package com.dboy.startup_coroutine

/**
 * Defines the execution mode for an [Initializer].
 *
 * This determines whether the task should be executed sequentially on the main thread
 * or concurrently on a background thread pool.
 *
 * --- (中文说明) ---
 *
 * 定义 [Initializer] 的执行模式。
 *
 * 它决定了任务是在主线程上串行执行，还是在后台线程池中并行执行。
 *
 * @see Initializer.initMode
 */
enum class InitMode {

    /**
     * **Serial Execution.**
     *
     * The task will be executed on the **main thread** in the order determined by the dependency graph.
     * This mode is suitable for:
     * - Tasks that must run on the main thread (e.g., UI-related initializations).
     * - Quick tasks that have strict execution order requirements.
     *
     * **Warning**: Do not perform long-running blocking operations in a SERIAL task,
     * as it will block the main thread and may cause an Application Not Responding (ANR) error.
     *
     * --- (中文说明) ---
     *
     * **串行执行模式。**
     *
     * 任务将会在 **主线程** 上，按照依赖关系决定的顺序执行。
     * 此模式适用于：
     * - 必须在主线程上运行的任务（例如，涉及UI的初始化）。
     * - 有严格执行顺序要求且耗时较短的任务。
     *
     * **警告**：请勿在串行任务中执行长时间的阻塞操作，因为它会阻塞主线程，可能导致应用程序无响应（ANR）。
     */
    SERIAL,


    /**
     * **Parallel Execution.**
     *
     * The task will be executed concurrently with other parallel tasks on a **background thread pool**
     * (`Dispatchers.Default`). This leverages multi-core CPUs to speed up the startup process.
     *
     * This mode is ideal for:
     * - I/O-bound operations (e.g., network requests, disk reads).
     * - CPU-bound operations (e.g., complex computations, data parsing).
     * - Any task that does not require immediate access to the main thread.
     *
     * --- (中文说明) ---
     *
     * **并行执行模式。**
     *
     * 任务将会在 **后台线程池** (`Dispatchers.Default`) 中，与其他并行任务并发执行。
     * 这能充分利用多核CPU，以加速启动过程。
     *
     * 此模式是以下场景的理想选择：
     * - I/O密集型操作（如：网络请求、磁盘读写）。
     * - CPU密集型操作（如：复杂计算、数据解析）。
     * - 任何不需要立即访问主线程的耗时任务。
     */
    PARALLEL
}