package com.dboy.startup.coroutine.imp

import android.app.Application
import com.dboy.startup.coroutine.Startup
import com.dboy.startup.coroutine.StartupDispatchers
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.IPrinter
import com.dboy.startup.coroutine.api.IStartup
import com.dboy.startup.coroutine.api.ITopologySorting
import com.dboy.startup.coroutine.api.Initializer
import com.dboy.startup.coroutine.model.StartupException
import com.dboy.startup.coroutine.model.StartupResult
import com.dboy.startup.coroutine.model.TaskMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

internal class StartupImpl(
    private val application: Application,
    private val isDebug: Boolean = false,
    private val dispatchers: StartupDispatchers = StartupDispatchers.Default,
    private val initializers: List<Initializer<*>>,
    private val onResult: ((StartupResult) -> Unit)? = null
) : DependenciesProvider, IStartup {

    private val logger: IPrinter = DefaultPrinter() // 默认日志实现
    private val topologySorting: ITopologySorting = DefaultTopologySorting()

    // Stores the results of each initializer.
    // 用于存储每个初始化任务的结果。
    private val results = ConcurrentHashMap<KClass<out Initializer<*>>, Any>()

    // CoroutineScope 用于管理所有初始化任务的生命周期。
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatchers.startDispatcher)

    // Atomic flag to prevent multiple invocations of the start() method.
    // 原子状态锁，防止 start() 方法被多次调用。
    private val started = AtomicBoolean(false)

    // [新增] 用于存储所有任务性能指标的列表
    private val taskMetrics = mutableListOf<TaskMetrics>()

    // 存储启动任务的Job，以便于外部控制（如取消或加入）
    private var startupJob: Job? = null


    override fun start(): Job {
        // 使用 compareAndSet 确保 start 逻辑只执行一次
        // Use compareAndSet to ensure that the start logic is executed only once
        if (started.compareAndSet(false, true)) {
            startupJob = scope.launch {
                executeStartupLogic()
            }
        }
        return startupJob!!
    }

    private suspend fun executeStartupLogic() {
        // 记录总启动流程的开始时间
        val totalStartTime = System.currentTimeMillis()
        val exceptions: MutableList<StartupException> = mutableListOf()
        try {

            val sortedInitializers = executeTopologySorting()

            supervisorScope {
                val parallelJobs = mutableMapOf<KClass<out Initializer<*>>, Deferred<*>>()
                for (initializer in sortedInitializers) {
                    val job = async(dispatchers.executeDispatcher) {
                        // Before starting the current task, wait for its dependencies to complete.
                        // 在启动当前任务前，先等待其所有依赖项完成。
                        val dependencyJobs = initializer.dependencies()
                            .mapNotNull { dependencyClass -> parallelJobs[dependencyClass] }

                        // 等待所有并行的依赖任务完成
                        // 如果任何依赖项失败，awaitAll会抛出异常，此任务也将失败
                        dependencyJobs.awaitAll()

                        // After all dependencies are met, execute the current task.
                        // 所有依赖都完成后，执行当前任务。
                        truthExecute(initializer)
                    }
                    parallelJobs[initializer::class] = job
                }

                // Wait for all parallel jobs to complete (successfully or with failure).
                // 等待所有并行任务完成（无论成功或失败）。
                parallelJobs.values.joinAll()
                //异常搜集
                exceptionsCollect(parallelJobs, exceptions)
            }
        } catch (e: Throwable) {
            // Catches all exceptions from the startup process, including failures from serial tasks
            // and composite exceptions from parallel tasks.
            // 捕获所有在启动流程中发生的异常，包括串行任务的失败和并行任务的复合异常。
            if (exceptions.isEmpty()) {
                // 对于无法确定来源的异常（如拓扑排序失败或串行任务失败），
                // 我们可以使用一个特殊的 KClass 或 null。这里用 Startup::class 代表框架自身错误。
                exceptions.add(StartupException(StartupImpl::class, e))
            }
        } finally {
            //  结果处理和日志记录
            printResult(totalStartTime, exceptions.isNotEmpty())

            // If the scope was cancelled, ensure a CancellationException is reported.
            // 如果 scope 被主动取消，确保一个 CancellationException 被报告。
            if (scope.coroutineContext[Job]?.isCancelled == true && exceptions.none { it.exception is CancellationException }) {
                // 如果是被取消的，并且异常列表里还没有 CancellationException，就手动添加一个
                exceptions.add(
                    StartupException(
                        StartupImpl::class,
                        CancellationException("Startup was cancelled.")
                    )
                )
            }

            //检查是否有异常任务
            val result = if (exceptions.isNotEmpty()) {
                StartupResult.Failure(exceptions)
            } else {
                StartupResult.Success
            }
            Startup.markInitializedResult(result)
            withContext(Dispatchers.Main + NonCancellable) {
                onResult?.invoke(result)
            }
            started.set(false)
        }

    }

    /**
     * 打印结果
     */
    private fun printResult(
        totalStartTime: Long,
        hasError: Boolean
    ) {
        if (isDebug) {
            val totalDuration = System.currentTimeMillis() - totalStartTime
            logger.printRunningTimeConsumingSummaries(
                totalDuration,
                taskMetrics,
                StartupDispatchers.getDispatchersMode(dispatchers),
                hasError
            )
        }
    }

    /**
     * 异常搜集
     */
    private suspend fun exceptionsCollect(
        parallelJobs: MutableMap<KClass<out Initializer<*>>, Deferred<*>>,
        exceptions: MutableList<StartupException>
    ) {
        // 我们需要一个从 Job 到 Initializer 的反向映射
        val jobToInitializerMap = parallelJobs.entries.associate { (k, v) -> v to k }
        // 手动收集所有非取消异常
        parallelJobs.values.forEach { job ->
            // 我们只检查已被取消/失败的任务。
            // 这个 if 条件至关重要：它强制 await() 进入“抛出取消异常”的逻辑分支，
            if (job.isCancelled) {
                try {
                    // 由于我们已经调用了 joinAll()，因此 await() 不会在这里暂停。
                    // 它将立即返回结果或抛出存储的异常。
                    job.await()
                } catch (e: CancellationException) {
                    // 我们显式忽略 CancellationExceptions，因为它们通常不是此逻辑失败的根本原因。
                    val rootCause = e.cause
                    if (rootCause != null) {
                        jobToInitializerMap[job]?.let { failedClass ->
                            taskMetrics.add(
                                TaskMetrics(
                                    name = failedClass.simpleName ?: "UnknownInitializer",
                                    duration = -1,
                                    threadName = "Error"
                                )
                            )
                            exceptions.add(StartupException(failedClass, rootCause))
                        }
                    }
                } catch (e: Throwable) {
                    // 这是一次“真正的”失败。将其添加到我们的例外列表中。
                    jobToInitializerMap[job]?.let { failedClass ->
                        taskMetrics.add(
                            TaskMetrics(
                                name = failedClass.simpleName ?: "UnknownInitializer",
                                duration = -1,
                                threadName = "Error"
                            )
                        )
                        exceptions.add(StartupException(failedClass, e))
                    }
                }
            }
        }
    }

    private fun executeTopologySorting(): List<Initializer<*>> {
        val analyze = topologySorting.topologySorting(initializers)
        if (isDebug) {
            logger.printTopologySortingDependGraph(analyze)
        }
        return analyze
    }

    private suspend fun truthExecute(initializer: Initializer<*>) {
        val duration = measureTimeMillis {
            val result = initializer.init(application, this)
            if (result != null) {
                results[initializer::class] = result
            }
        }
        if (isDebug) {
            taskMetrics.add(
                TaskMetrics(
                    name = initializer::class.simpleName ?: "UnknownInitializer",
                    duration = duration,
                    threadName = Thread.currentThread().name
                )
            )
        }
    }

    /**
     * 取消所有正在进行的初始化任务。
     */
    override fun cancel() {
        scope.cancel("Startup cancelled by caller.")
    }



    @Suppress("UNCHECKED_CAST")
    override fun <T> result(dependency: KClass<out Initializer<*>>): T {
        val any = results[dependency]
        if (any != null) {
            return any as T
        }
        // 如果 value 为 null，抛出异常
        throw IllegalStateException(
            "Result for ${dependency.simpleName} not found. " +
                    "Ensure that:\n" +
                    "1. The initializer '${dependency.simpleName}' is included in the startup list.\n" +
                    "2. It has executed successfully.\n" +
                    "3. It does not return null explicitly (Initializer<Unit> returns Unit object, which is fine)."
        )
    }


    @Suppress("UNCHECKED_CAST")
    override fun <T> resultOrNull(dependency: KClass<out Initializer<*>>): T? {
        return results[dependency] as? T
    }

}
