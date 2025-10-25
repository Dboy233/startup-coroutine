@file:Suppress("NonAsciiCharacters")

package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class StartupTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mockContext: Context = mock(Context::class.java)

    @Before
    fun setup() {
        logList.clear()
    }

    @After
    fun tearDown() {
        println("Logs for test '${Thread.currentThread().stackTrace[2].methodName}':")
        logList.forEach { println("  $it") }
        println("------------------------------------")
    }

    /**
     * MODIFIED: runTest 辅助函数更新
     *
     * runTest 的作用域是 testDispatcher。由于 Startup 在 Dispatchers.IO 上启动，
     * 我们需要在 runTest 的作用域内启动 Startup，并使用 suspendCancellableCoroutine
     * 等待其完成，这样可以桥接两个不同的上下文。
     */
    private suspend fun runTest(
        initializers: List<Initializer<*>>,
        onCompletion: () -> Unit,
        onError: ((List<Throwable>) -> Unit)? = null
    ) {
        suspendCancellableCoroutine { continuation ->
            val startup = Startup(
                context = mockContext,
                initializers = initializers,
                onCompletion = {
                    onCompletion()
                    continuation.resume(Unit)
                },
                onError = { e ->
                    onError?.invoke(e)
                    continuation.resume(Unit)
                }
            )
            startup.start()
        }
    }

    @Test
    fun `1 - 串行任务的单一依赖`() = runTest {
        var completed = false
        runTest(listOf(SerialTaskA(), SerialTaskB()), onCompletion = {
            completed = true
            assertEquals(2, logList.size)
            assertTrue(logList[0].startsWith("SerialTaskA on TestMain"))
            assertTrue(logList[1].startsWith("SerialTaskB on TestMain"))
        }, onError = {
            fail("Test should not fail. Errors: ${it.joinToString { e -> e.message ?: "Unknown" }}")
        })
        assertTrue(completed)
    }

    @Test
    fun `2 - 串行任务的多依赖`() = runTest {
        var completed = false
        runTest(listOf(SerialTaskA(), SerialTaskB(), SerialTaskC()), onCompletion = {
            completed = true
            assertEquals(3, logList.size)
            assertEquals("SerialTaskA", logList[0].substringBefore(" on"))
            assertEquals("SerialTaskB", logList[1].substringBefore(" on"))
            assertEquals("SerialTaskC", logList[2].substringBefore(" on"))
            // 所有任务都应在主线程上执行
            assertTrue(logList.all { it.contains(" on TestMain") })
        })
        assertTrue(completed)
    }

    @Test
    fun `3 - 并行任务依赖单个串行任务`() = runTest {
        runTest(listOf(SerialTaskA(), ParallelTaskD()), onCompletion = {
            assertEquals(2, logList.size)
            val taskNames = logList.map { it.substringBefore(" on") }
            assertTrue(taskNames.indexOf("SerialTaskA") < taskNames.indexOf("ParallelTaskD"))
            // MODIFIED: 并行任务现在也在主线程启动
            assertTrue(logList[0].contains(" on TestMain"))
            assertTrue(logList[1].contains(" on TestMain"))
        })
    }

    @Test
    fun `4 - 并行任务依赖多个串行任务`() = runTest {
        runTest(listOf(SerialTaskA(), SerialTaskB(), ParallelTaskE()), onCompletion = {
            assertEquals(3, logList.size)
            val taskNames = logList.map { it.substringBefore(" on") }
            assertTrue(taskNames.indexOf("SerialTaskA") < taskNames.indexOf("ParallelTaskE"))
            assertTrue(taskNames.indexOf("SerialTaskB") < taskNames.indexOf("ParallelTaskE"))
            assertTrue(logList.all { it.contains(" on TestMain") })
        })
    }

    @Test
    fun `5 - 并行任务依赖单个并行任务`() = runTest {
        runTest(listOf(ParallelTaskF(), ParallelTaskG()), onCompletion = {
            assertEquals(2, logList.size)
            val taskNames = logList.map { it.substringBefore(" on") }
            assertTrue(taskNames.indexOf("ParallelTaskF") < taskNames.indexOf("ParallelTaskG"))
            // MODIFIED: 并行任务现在也在主线程启动
            assertTrue(logList[0].contains(" on TestMain"))
            assertTrue(logList[1].contains(" on TestMain"))
        })
    }

    @Test
    fun `6 - 并行任务依赖多个并行任务`() = runTest {
        runTest(listOf(ParallelTaskF(), ParallelTaskG(), ParallelTaskH()), onCompletion = {
            assertEquals(3, logList.size)
            val taskNames = logList.map { it.substringBefore(" on") }
            assertTrue(taskNames.indexOf("ParallelTaskF") < taskNames.indexOf("ParallelTaskH"))
            assertTrue(taskNames.indexOf("ParallelTaskG") < taskNames.indexOf("ParallelTaskH"))
            assertTrue(logList.all { it.contains(" on TestMain") })
        })
    }

    @Test
    fun `7 - 依赖关系发生环形依赖异常测试`() = runTest {
        var caughtException: Throwable? = null
        runTest(
            initializers = listOf(CircularTaskI(), CircularTaskJ()),
            onCompletion = { fail("Should have thrown an exception") },
            onError = { caughtException = it.first() }
        )
        assertNotNull(caughtException)
        assertTrue(caughtException is IllegalStateException)
        assertTrue(caughtException!!.message!!.contains("Circular dependency detected"))
        assertTrue(logList.isEmpty())
    }

    @Test
    fun `8 - 串行任务依赖了并行任务测试`() = runTest {
        var caughtException: Throwable? = null
        runTest(
            initializers = listOf(ParallelTaskF(), InvalidDependencyTaskK()),
            onCompletion = { fail("Should have thrown an exception") },
            onError = { caughtException = it.first() }
        )
        assertNotNull(caughtException)
        assertTrue(caughtException is IllegalStateException)
        assertTrue(caughtException!!.message!!.contains("Illegal dependency"))
        assertTrue(logList.isEmpty())
    }

    @Test
    fun `9 - 混合依赖测试`() = runTest {
        runTest(
            listOf(
                SerialTaskA(),
                SerialTaskB(),
                ParallelTaskF(),
                ParallelTaskG(),
                MixedDependencyTaskL()
            ), onCompletion = {
                assertEquals(5, logList.size)
                val taskNames = logList.map { it.substringBefore(" on") }
                assertTrue(taskNames.indexOf("SerialTaskA") < taskNames.indexOf("MixedDependencyTaskL"))
                assertTrue(taskNames.indexOf("SerialTaskB") < taskNames.indexOf("MixedDependencyTaskL"))
                assertTrue(taskNames.indexOf("ParallelTaskF") < taskNames.indexOf("MixedDependencyTaskL"))
                assertTrue(taskNames.indexOf("ParallelTaskG") < taskNames.indexOf("MixedDependencyTaskL"))
                assertTrue(logList.all { it.contains(" on TestMain") })
            })
    }

    @Test
    fun `10 - 并行任务异常传播测试`() = runTest {
        var caughtExceptions: List<Throwable>? = null
        runTest(
            initializers = listOf(ExceptionTaskM(), DependentOnExceptionTaskN()),
            onCompletion = { fail("Should have triggered onError") },
            onError = { caughtExceptions = it }
        )
        assertNotNull(caughtExceptions)
        assertTrue(caughtExceptions!!.isNotEmpty())
        assertEquals("M task failed deliberately!", caughtExceptions.first().message)
        assertTrue(logList.any { it.startsWith("ExceptionTaskM_Start") })
        assertFalse(logList.any { it.startsWith("DependentOnExceptionTaskN") })
    }

    @Test
    fun `11 - 取消操作测试`() = runTest {

        val errorCompletable = CompletableDeferred<List<Throwable>>()
        val taskStartedCompletable = CompletableDeferred<Unit>()

        val initializerList = listOf(
            SerialTaskA(),
            ControllableLongRunningTask(taskStartedCompletable), // 使用可控任务
            SerialTaskB()
        )

        val startup = Startup(
            context = mockContext,
            initializers = initializerList,
            onCompletion = { fail("任务应被取消，不应执行 onCompletion") },
            onError = { exceptions -> errorCompletable.complete(exceptions) }
        )

        // 1. 在一个独立的后台协程中启动整个流程，以模拟真实环境
        val testJob = launch {
            // 启动 Startup 流程
            val startupJob = launch { startup.start() }

            // 2. 等待，直到 ControllableLongRunningTask 明确通知我们它已开始
            //    这确保了我们不会过早地取消
            withTimeout(2000) { // 给它一个合理的时间开始
                taskStartedCompletable.await()
            }

            // 3. 现在我们确定任务正在运行，可以安全地取消它
            startup.cancel()

            // 4. 等待 onError 回调被触发
            val caughtExceptions = withTimeout(2000) {
                errorCompletable.await()
            }

            // 5. 断言结果
            assertNotNull(caughtExceptions)
            assertTrue(caughtExceptions.any { it is CancellationException })

            // 6. 等待 startup 的协程完全结束，以确保所有清理工作完成
            startupJob.join()
        }

        // 等待整个测试流程完成
        testJob.join()

        // 在测试协程的末尾进行最终的日志断言
        // LongRunningTask 之前的任务应该已完成
        assertTrue(logList.any { it.startsWith("SerialTaskA") })
        // LongRunningTask 已经开始
        assertTrue(logList.any { it.startsWith("LongRunningTask_Start") })
        // LongRunningTask 之后的任务不应执行
        assertFalse(logList.any { it.startsWith("SerialTaskB") })
        // LongRunningTask 不应该完成
        assertFalse(logList.any { it.startsWith("LongRunningTask_Finish") })
    }
}

// 辅助类，用于自动设置和清理 Main dispatcher
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : org.junit.rules.TestWatcher() {
    override fun starting(description: org.junit.runner.Description) {
        Dispatchers.setMain(testDispatcher)
        // 增加一个标识，便于在日志中区分
        Thread.currentThread().name = "TestMain"
    }

    override fun finished(description: org.junit.runner.Description) {
        Dispatchers.resetMain()
        Thread.currentThread().name = "main" // 恢复
    }
}

// 一个可以通知外部它已开始的长时间运行任务
class ControllableLongRunningTask(
    private val started: CompletableDeferred<Unit>? = null
) : Initializer<Unit>() {
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(SerialTaskA::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) {
        log("LongRunningTask_Start")
        started?.complete(Unit) // 通知测试，任务已开始
        delay(1000)
        log("LongRunningTask_Finish")
    }
}

