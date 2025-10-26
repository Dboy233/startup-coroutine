@file:Suppress("NonAsciiCharacters")

package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class StartupTest {

    // Rule for controlling the Main dispatcher in tests.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Explicitly create a separate TestDispatcher for IO operations.
    // We use StandardTestDispatcher to better simulate real-world IO schedulers,
    // which queue tasks.
    private val ioTestDispatcher =
        StandardTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)


    private val mockContext: Context = mock(Context::class.java)

    // A helper function to create a default set of dispatchers for testing.
    // This makes tests cleaner and ensures consistency.
    private fun createTestDispatchers(
        start: TestDispatcher = ioTestDispatcher,
        execute: TestDispatcher = mainDispatcherRule.testDispatcher,
        callback: TestDispatcher = mainDispatcherRule.testDispatcher
    ) = StartupDispatchers(start, execute, callback)


    @Before
    fun setup() {
        // 每个测试开始前重置单例对象的状态
        listOf(
            S1, S2, S3, P1, P2, PA, PB, PC, PD, CycleA, CycleB, CycleC,
            IllegalDepSerial, P_MixC, FailingParallelA, DependentOnFailure, NormalParallelB
        ).forEach {
            it.callCount.set(0)
            it.executedAt = 0L
        }
    }

    @After
    fun tearDown() {
        // 测试后清理
    }


    @Test
    fun `1 - 串行任务的单一依赖`() = runTest {
        val s1 = S1
        val s2 = S2
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, s2),
            onCompletion = { completed = true },
            onError = { fail("onError should not be called") }
        )

        startup.start()
        advanceUntilIdle() // 执行所有排队的任务

        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, s2.callCount.get())
        assertTrue("S2 应该在 S1 之后执行", s2.executedAt >= s1.executedAt)
    }

    @Test
    fun `2 - 串行任务的多依赖`() = runTest {
        val s1 = S1
        val s2 = S2
        val s3 = S3
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, s2, s3),
            onCompletion = { completed = true },
            onError = { throw it.first() }
        )

        startup.start()
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, s2.callCount.get())
        assertEquals(1, s3.callCount.get())
        assertTrue("S2 应该在 S1 之后执行", s2.executedAt >= s1.executedAt)
        assertTrue("S3 应该在 S2 之后执行", s3.executedAt >= s2.executedAt)
    }

    @Test
    fun `3 - 并行任务依赖单个串行任务`() = runTest {
        val s1 = S1
        val p1 = P1
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, p1),
            onCompletion = { completed = true },
            onError = { throw it.first() }
        )

        startup.start()
        advanceUntilIdle()
        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, p1.callCount.get())
        assertTrue("P1 应该在 S1 之后执行", p1.executedAt >= s1.executedAt)
    }

    @Test
    fun `4 - 并行任务依赖多个串行任务`() = runTest {
        val s1 = S1
        val s2 = S2
        val p2 = P2
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, s2, p2),
            onCompletion = { completed = true },
            onError = { throw it.first() }
        )

        startup.start()
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, s2.callCount.get())
        assertEquals(1, p2.callCount.get())
        assertTrue(
            "P2 应该在 S1 和 S2 之后执行",
            p2.executedAt >= s1.executedAt && p2.executedAt >= s2.executedAt
        )
    }

    @Test
    fun `5 - 并行任务依赖单个并行任务`() = runTest {
        val pa = PA
        val pb = PB
        var completed = false
        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(pa, pb),
            onCompletion = { completed = true },
            onError = { throw it.first() }
        )

        startup.start()
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals(1, pa.callCount.get())
        assertEquals(1, pb.callCount.get())
        assertTrue("PB 应该在 PA 之后执行", pb.executedAt >= pa.executedAt)
    }

    @Test
    fun `6 - 并行任务依赖多个并行任务`() = runTest {
        val pa = PA
        val pb = PB
        val pc = PC
        val pd = PD
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(pa, pb, pc, pd),
            onCompletion = { completed = true },
            onError = { throw it.first() }
        )

        startup.start()
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals(1, pa.callCount.get())
        assertEquals(1, pb.callCount.get())
        assertEquals(1, pc.callCount.get())
        assertEquals(1, pd.callCount.get())
        assertTrue(
            "PD 应该在 PB 和 PC 之后执行",
            pd.executedAt >= pb.executedAt && pd.executedAt >= pc.executedAt
        )
    }

    @Test
    fun `7 - 依赖关系发生环形依赖异常测试`() = runTest {
        var capturedThrowable: Throwable? = null
        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(CycleA, CycleB, CycleC),
            onCompletion = { fail("onCompletion should not be called") },
            onError = { capturedThrowable = it.first() }
        )
        startup.start()
        advanceUntilIdle() // 允许 onError 回调执行

        Assert.assertNotNull(capturedThrowable)
        assertTrue(capturedThrowable is IllegalStateException)
        assertTrue(capturedThrowable?.message?.contains("Circular dependency") == true)
    }

    @Test
    fun `8 - 串行任务依赖了并行任务测试`() = runTest {
        var capturedThrowable: Throwable? = null
        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(PA, IllegalDepSerial),
            onCompletion = { fail("onCompletion should not be called") },
            onError = { capturedThrowable = it.first() }
        )
        startup.start()
        advanceUntilIdle() // 允许 onError 回调执行

        Assert.assertNotNull(capturedThrowable)
        assertTrue(capturedThrowable is IllegalStateException)
        assertTrue(capturedThrowable?.message?.contains("Illegal dependency") == true)
    }

    @Test
    fun `9 - 混合依赖测试`() = runTest {
        val s1 = S1
        val pa = PA
        val pMixC = P_MixC

        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, pa, pMixC),
            onCompletion = { completed = true },
            onError = { throw it.first() }
        )

        startup.start()

        advanceUntilIdle()

        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, pa.callCount.get())
        assertEquals(1, pMixC.callCount.get())
        assertTrue(
            "P_MixC 应该在 S1 和 PA 之后执行",
            pMixC.executedAt >= s1.executedAt && pMixC.executedAt >= pa.executedAt
        )
    }

    @Test
    fun `10 - 并行任务异常传播测试`() = runTest {
        var capturedErrors: List<Throwable>? = null
        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(FailingParallelA, DependentOnFailure),
            onCompletion = { fail("onCompletion should not be called") },
            onError = { capturedErrors = it }
        )
        startup.start()
        advanceUntilIdle()

        assertEquals(0, DependentOnFailure.callCount.get())
        Assert.assertNotNull(capturedErrors)
        val hasOriginalException =
            capturedErrors!!.any { it.message?.contains("FailingParallelA failed!") == true }
        assertTrue("错误列表应包含原始异常", hasOriginalException)
    }

// In StartupTest.kt

    // This is the final, correct version of test 11.

    @Test
    fun `11 - 取消操作测试`() = runTest {
        val cancellableTask = CancellableInitializer()
        var capturedErrors: List<Throwable>? = null

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(), // Now correctly uses StandardTestDispatcher
            initializers = listOf(S1, cancellableTask, S2),
            onCompletion = { fail("onCompletion should not be called") },
            onError = {
                capturedErrors = it
            }
        )

        // 1. 开始。任务被排队，但尚未执行。
        startup.start()

        // 2. 运行当前排队的任务。
        // 这将启动 `cancellableTask`，然后它会在 `await()` 处挂起。
        runCurrent()

        startup.cancel()

        // 4. 执行所有剩余的排队任务，这其中就包括了 onError 回调。
        advanceUntilIdle()

        // 5. 现在可以安全地进行断言。
        assertEquals(1, cancellableTask.callCount.get()) // 任务被启动过
        Assert.assertNotNull("取消后 capturedErrors 不应为 null", capturedErrors)
        assertTrue(
            "错误列表应包含 CancellationException",
            capturedErrors!!.any { it is CancellationException }
        )
    }


    @Test
    fun `12 - 并行任务单个异常不影响其他任务并行测试`() = runTest {
        val failingTask = FailingParallelA
        val normalTask = NormalParallelB
        var capturedErrors: List<Throwable>? = null

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(failingTask, normalTask),
            onCompletion = { /* 不应被调用 */ },
            onError = {
                capturedErrors = it
            }
        )

        startup.start()

        advanceUntilIdle()

        // 失败的任务被调用
        assertEquals(1, failingTask.callCount.get())
        // 正常的并行任务也应该被调用并完成
        assertEquals(1, normalTask.callCount.get())
        // 应该只捕获到一个错误
        assertEquals(1, capturedErrors?.size)
        assertTrue(capturedErrors?.first()?.message?.contains("FailingParallelA failed!") == true)
    }

    // 修复测试 13
    @Test
    fun `13 - 任务主动切换线程测试`() = runTest {
        val switcher = ThreadSwitchingInitializer().apply {
            dispatcherProvider = { ioTestDispatcher }
        }
        var resultFromSwitcher: String? = null
        var completed = false

        val resultReader = ResultInitializer("ResultReader") { _, provider ->
            resultFromSwitcher = provider.resultOrNull<String>(ThreadSwitchingInitializer::class)
            "ResultReaderDone"
        }.apply {
            dependencies = listOf(ThreadSwitchingInitializer::class)
        }

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(execute = mainDispatcherRule.testDispatcher),
            initializers = listOf(switcher, resultReader),
            onCompletion = { completed = true },
            onError = { fail("onError should not be called: ${it.firstOrNull()?.message}") }
        )

        startup.start()
        advanceUntilIdle() // 执行所有任务，包括在 ioTestDispatcher 上排队的

        assertTrue(completed)
        assertEquals(1, switcher.callCount.get())
        Assert.assertNotNull(resultFromSwitcher)
        assertTrue(resultFromSwitcher!!.contains("InitialThread: TestMain"))
        println("Thread switching result: $resultFromSwitcher")
    }
}


@ExperimentalCoroutinesApi
class MainDispatcherRule(
    // By default, we use UnconfinedTestDispatcher for Main thread ops to simplify most tests.
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
        Thread.currentThread().name = "TestMain"
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        Thread.currentThread().name = "main"
    }
}
