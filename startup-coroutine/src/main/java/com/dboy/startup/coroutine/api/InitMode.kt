package com.dboy.startup.coroutine.api

/**
 * Defines the execution strategy for an [Initializer] within the startup process.
 *
 * This enum controls how tasks are ordered and executed relative to each other.
 * This allows for safe UI operations by default. Developers are responsible for moving
 * any long-running or blocking operations to a background thread using `withContext`.
 *
 * --- (中文说明) ---
 *
 * 定义 [Initializer] 在启动流程中的执行策略。
 *
 * 这个枚举主要控制任务之间的执行顺序关系。
 *
 * @see Initializer.initMode
 */
enum class InitMode {

    /**
     * **Serial Execution Strategy.**
     *
     * Tasks marked as `SERIAL` will be executed one after another, in the order
     * determined by their dependencies.
     *
     * - **Use Case**: Suitable for a sequence of tasks that have a strict, non-parallelizable
     *   order (e.g., Task B must run only after Task A is fully complete).
     *
     * **Guidance**: If the task with [com.dboy.startup.coroutine.DefaultDispatchers] will start on the main thread, you can directly perform
     * quick, main-thread-only operations. For any potentially time-consuming work
     * (like I/O or heavy computation), you **must** wrap it in `withContext(Dispatchers.IO)`
     * or `withContext(Dispatchers.Default)` to avoid blocking the UI.
     *
     * --- (中文说明) ---
     *
     * **串行执行策略。**
     *
     * 标记为 `SERIAL` 的任务将会按照其依赖关系决定的顺序，一个接一个地执行。
     *
     * - **适用场景**: 适用于具有严格、不可并行顺序的一系列任务（例如，任务B必须在任务A完全完成后才能运行）。
     *
     * **使用指导**: 如果使用[com.dboy.startup.coroutine.DefaultDispatchers]任务将在主线程上启动，你可以直接执行那些耗时短且必须在主线程的操作。
     * 对于任何可能耗时的工作（如 I/O 或复杂计算），你 **必须** 将其包裹在
     * `withContext(Dispatchers.IO)` 或 `withContext(Dispatchers.Default)` 中，以避免阻塞UI。
     *
     * 串行任务依赖的任务也必须是串行的.
     */
    SERIAL,


    /**
     * **Parallel Execution Strategy.**
     *
     * Tasks marked as `PARALLEL` are eligible to run concurrently with other `PARALLEL`
     * tasks, once their dependencies are met. The framework leverages coroutines to
     * manage this concurrency efficiently.
     *
     * - **Execution Thread**: If the task with [com.dboy.startup.coroutine.DefaultDispatchers] will start on the main thread. Concurrency is achieved
     *   through non-blocking suspension and resumption, managed by the coroutine scheduler.
     * - **Use Case**: The default choice for most tasks. Ideal for any initializer that can
     *   run independently of others (once its direct dependencies are satisfied).
     *
     * **Guidance**: Just like `SERIAL` tasks, execution begins on the main thread.
     * It is crucial to delegate any blocking or long-running operations to a background
     * dispatcher using `withContext`. The "parallel" nature refers to the logical execution
     * flow, allowing the framework to interleave tasks efficiently, not necessarily to
     * multi-threaded execution unless you explicitly introduce it.
     *
     * Parallel tasks can rely on multiple serial/parallel tasks.
     *
     * --- (中文说明) ---
     *
     * **并行执行策略。**
     *
     * 标记为 `PARALLEL` 的任务，在它们的依赖项被满足后，将有资格与其他 `PARALLEL` 任务并发执行。
     * 框架通过协程来高效地管理这种并发性。
     *
     * - **执行线程**: 如果使用[com.dboy.startup.coroutine.DefaultDispatchers]任务将在主线程上启动. 其并发性是通过协程调度器的非阻塞式挂起和恢复来实现的。
     * - **适用场景**: 大多数任务的默认选择。适用于任何可以独立于其他任务运行的初始化程序（一旦其直接依赖项被满足）。
     *
     * **使用指导**: 与 `SERIAL` 任务一样，执行也是在主线程上启动。因此，将任何阻塞或长时间运行的操作
     * 通过 `withContext` 委托给后台调度器是至关重要的。“并行”的特性指的是逻辑执行流，它允许框架高效地
     * 交错执行任务，而并非指任务一定在多个线程上执行——除非你明确地引入了多线程。
     *
     * 并行任务可以依赖多个串行/并行任务.
     */
    PARALLEL
}
