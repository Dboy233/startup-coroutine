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

/**
 * `Startup` 框架的核心单元测试类。
 *
 * 本测试类利用 `kotlinx-coroutines-test` 库提供的工具（如 `runTest`, `StandardTestDispatcher`）
 * 来验证 `Startup` 框架在各种场景下的行为是否符合预期。测试覆盖了依赖关系处理、
 * 并发与串行执行、异常捕获与传播、取消操作以及线程切换等核心功能。
 *
 * 通过将所有调度器替换为 `StandardTestDispatcher`，测试获得了完全可控和可预测的执行顺序，
 * 消除了多线程和真实时间带来的不确定性。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupTest {

    /**
     * JUnit Rule，用于在每个测试前后自动设置和重置 `Dispatchers.Main`。
     *
     * **关键实践**: 我们将默认的 `testDispatcher` 设置为 `StandardTestDispatcher`。
     * 这强制所有依赖主线程的协程任务都进入队列，而不是立即执行，从而使测试行为
     * 完全可控，所有任务的执行都需要通过 `advanceUntilIdle()` 或 `runCurrent()` 手动触发。
     */
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * 为模拟 IO 操作而创建的一个独立的测试调度器。
     * 它与 `mainDispatcherRule` 共享同一个 `TestCoroutineScheduler`，以确保虚拟时间同步。
     * 使用 `StandardTestDispatcher` 可以更好地模拟真实 IO 调度器的排队行为。
     */
    private val ioTestDispatcher =
        StandardTestDispatcher(mainDispatcherRule.testDispatcher.scheduler)

    // Mock Android Context，因为 Initializer 的 init 方法需要它。
    private val mockContext: Context = mock(Context::class.java)

    /**
     * 辅助函数，用于创建一套供 `Startup` 实例使用的测试调度器。
     * 这使得在每个测试中注入调度器变得更简洁，并确保了配置的一致性。
     * 默认情况下，所有调度器都使用 `mainDispatcherRule` 的 `TestDispatcher`，
     * 除非在特定测试中需要覆盖（例如，测试 IO 相关的场景）。
     */
    private fun createTestDispatchers(
        start: TestDispatcher = ioTestDispatcher,
        execute: TestDispatcher = mainDispatcherRule.testDispatcher,
        callback: TestDispatcher = mainDispatcherRule.testDispatcher
    ) = StartupDispatchers(start, execute, callback)


    /**
     * 在每个测试方法执行前运行。
     * 主要负责重置所有测试用到的 `Initializer` 单例对象的状态（如调用次数、执行时间），
     * 确保每个测试用例都在一个干净、独立的环境中开始。
     */
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

    /**
     * 在每个测试方法执行后运行。
     * 目前为空，可用于未来的清理逻辑。
     */
    @After
    fun tearDown() {
        // 测试后清理
    }

    /**
     * 测试场景 1: 验证一个串行任务（S2）能正确地等待其单个串行依赖（S1）完成。
     */
    @Test
    fun `1 - 串行任务的单一依赖`() = runTest {
        println("---------- 测试 1: 串行单一依赖 开始 ----------")
        val s1 = S1
        val s2 = S2
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, s2),
            onCompletion = {
                println("测试 1: onCompletion 回调被触发")
                completed = true
            },
            onError = { fail("onError 不应被调用") }
        )

        println("测试 1: 调用 startup.start()")
        startup.start()

        println("测试 1: 调用 advanceUntilIdle() 来执行所有排队任务...")
        // 关键步骤: 执行所有已排队的协程任务，直到调度器空闲。
        // 由于我们使用 StandardTestDispatcher，这一步是必需的。
        advanceUntilIdle()
        println("测试 1: advanceUntilIdle() 执行完毕")

        println("测试 1: 开始断言...")
        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, s2.callCount.get())
        assertTrue("S2 应该在 S1 之后执行", s2.executedAt >= s1.executedAt)
        println("---------- 测试 1: 成功结束 ----------\n")
    }

    /**
     * 测试场景 2: 验证一个串行任务（S3）能正确地等待其多个串行依赖（S1, S2）全部完成。
     */
    @Test
    fun `2 - 串行任务的多依赖`() = runTest {
        println("---------- 测试 2: 串行多依赖 开始 ----------")
        val s1 = S1
        val s2 = S2
        val s3 = S3
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, s2, s3),
            onCompletion = {
                println("测试 2: onCompletion 回调被触发")
                completed = true
            },
            onError = { throw it.first() }
        )

        println("测试 2: 调用 startup.start()")
        startup.start()
        println("测试 2: 调用 advanceUntilIdle() 来执行所有排队任务...")
        advanceUntilIdle()
        println("测试 2: advanceUntilIdle() 执行完毕")

        println("测试 2: 开始断言...")
        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, s2.callCount.get())
        assertEquals(1, s3.callCount.get())
        assertTrue("S2 应该在 S1 之后执行", s2.executedAt >= s1.executedAt)
        assertTrue("S3 应该在 S2 之后执行", s3.executedAt >= s2.executedAt)
        println("---------- 测试 2: 成功结束 ----------\n")
    }

    /**
     * 测试场景 3: 验证一个并行任务（P1）能正确地等待其单个串行依赖（S1）完成。
     */
    @Test
    fun `3 - 并行任务依赖单个串行任务`() = runTest {
        println("---------- 测试 3: 并行依赖单个串行 开始 ----------")
        val s1 = S1
        val p1 = P1
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, p1),
            onCompletion = {
                println("测试 3: onCompletion 回调被触发")
                completed = true
            },
            onError = { throw it.first() }
        )

        println("测试 3: 调用 startup.start()")
        startup.start()
        println("测试 3: 调用 advanceUntilIdle() 来执行所有排队任务...")
        advanceUntilIdle()
        println("测试 3: advanceUntilIdle() 执行完毕")

        println("测试 3: 开始断言...")
        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, p1.callCount.get())
        assertTrue("P1 应该在 S1 之后执行", p1.executedAt >= s1.executedAt)
        println("---------- 测试 3: 成功结束 ----------\n")
    }

    /**
     * 测试场景 4: 验证一个并行任务（P2）能正确地等待其多个串行依赖（S1, S2）全部完成。
     */
    @Test
    fun `4 - 并行任务依赖多个串行任务`() = runTest {
        println("---------- 测试 4: 并行依赖多个串行 开始 ----------")
        val s1 = S1
        val s2 = S2
        val p2 = P2
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, s2, p2),
            onCompletion = {
                println("测试 4: onCompletion 回调被触发")
                completed = true
            },
            onError = { throw it.first() }
        )

        println("测试 4: 调用 startup.start()")
        startup.start()
        println("测试 4: 调用 advanceUntilIdle() 来执行所有排队任务...")
        advanceUntilIdle()
        println("测试 4: advanceUntilIdle() 执行完毕")

        println("测试 4: 开始断言...")
        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, s2.callCount.get())
        assertEquals(1, p2.callCount.get())
        assertTrue(
            "P2 应该在 S1 和 S2 之后执行",
            p2.executedAt >= s1.executedAt && p2.executedAt >= s2.executedAt
        )
        println("---------- 测试 4: 成功结束 ----------\n")
    }

    /**
     * 测试场景 5: 验证一个并行任务（PB）能正确地等待其单个并行依赖（PA）完成。
     */
    @Test
    fun `5 - 并行任务依赖单个并行任务`() = runTest {
        println("---------- 测试 5: 并行依赖单个并行 开始 ----------")
        val pa = PA
        val pb = PB
        var completed = false
        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(pa, pb),
            onCompletion = {
                println("测试 5: onCompletion 回调被触发")
                completed = true
            },
            onError = { throw it.first() }
        )

        println("测试 5: 调用 startup.start()")
        startup.start()
        println("测试 5: 调用 advanceUntilIdle() 来执行所有排队任务...")
        advanceUntilIdle()
        println("测试 5: advanceUntilIdle() 执行完毕")

        println("测试 5: 开始断言...")
        assertTrue(completed)
        assertEquals(1, pa.callCount.get())
        assertEquals(1, pb.callCount.get())
        assertTrue("PB 应该在 PA 之后执行", pb.executedAt >= pa.executedAt)
        println("---------- 测试 5: 成功结束 ----------\n")
    }

    /**
     * 测试场景 6: 验证一个并行任务（PD）能正确地等待其多个并行依赖（PB, PC）全部完成。
     */
    @Test
    fun `6 - 并行任务依赖多个并行任务`() = runTest {
        println("---------- 测试 6: 并行依赖多个并行 开始 ----------")
        val pa = PA
        val pb = PB
        val pc = PC
        val pd = PD
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(pa, pb, pc, pd),
            onCompletion = {
                println("测试 6: onCompletion 回调被触发")
                completed = true
            },
            onError = { throw it.first() }
        )

        println("测试 6: 调用 startup.start()")
        startup.start()
        println("测试 6: 调用 advanceUntilIdle() 来执行所有排队任务...")
        advanceUntilIdle()
        println("测试 6: advanceUntilIdle() 执行完毕")

        println("测试 6: 开始断言...")
        assertTrue(completed)
        assertEquals(1, pa.callCount.get())
        assertEquals(1, pb.callCount.get())
        assertEquals(1, pc.callCount.get())
        assertEquals(1, pd.callCount.get())
        assertTrue(
            "PD 应该在 PB 和 PC 之后执行",
            pd.executedAt >= pb.executedAt && pd.executedAt >= pc.executedAt
        )
        println("---------- 测试 6: 成功结束 ----------\n")
    }

    /**
     * 测试场景 7: 验证当任务间存在循环依赖时，框架能正确抛出 `IllegalStateException`。
     */
    @Test
    fun `7 - 依赖关系发生环形依赖异常测试`() = runTest {
        println("---------- 测试 7: 环形依赖异常 开始 ----------")
        var capturedThrowable: Throwable? = null
        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(CycleA, CycleB, CycleC),
            onCompletion = { fail("onCompletion 不应被调用") },
            onError = {
                println("测试 7: onError 回调被触发，错误: ${it.firstOrNull()?.message}")
                capturedThrowable = it.first()
            }
        )

        println("测试 7: 调用 startup.start()")
        startup.start()
        // 允许启动流程中的同步异常被捕获，并调度 onError 回调。
        println("测试 7: 调用 advanceUntilIdle() 以触发 onError 回调...")
        advanceUntilIdle()
        println("测试 7: advanceUntilIdle() 执行完毕")

        println("测试 7: 开始断言...")
        Assert.assertNotNull(capturedThrowable)
        assertTrue(capturedThrowable is IllegalStateException)
        assertTrue(capturedThrowable?.message?.contains("Circular dependency") == true)
        println("---------- 测试 7: 成功捕获预期异常，结束 ----------\n")
    }

    /**
     * 测试场景 8: 验证当一个串行任务非法地依赖于一个并行任务时，框架能正确抛出 `IllegalStateException`。
     */
    @Test
    fun `8 - 串行任务依赖了并行任务测试`() = runTest {
        println("---------- 测试 8: 串行非法依赖并行异常 开始 ----------")
        var capturedThrowable: Throwable? = null
        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(PA, IllegalDepSerial),
            onCompletion = { fail("onCompletion 不应被调用") },
            onError = {
                println("测试 8: onError 回调被触发，错误: ${it.firstOrNull()?.message}")
                capturedThrowable = it.first()
            }
        )

        println("测试 8: 调用 startup.start()")
        startup.start()
        println("测试 8: 调用 advanceUntilIdle() 以触发 onError 回调...")
        advanceUntilIdle()
        println("测试 8: advanceUntilIdle() 执行完毕")


        println("测试 8: 开始断言...")
        Assert.assertNotNull(capturedThrowable)
        assertTrue(capturedThrowable is IllegalStateException)
        assertTrue(capturedThrowable?.message?.contains("Illegal dependency") == true)
        println("---------- 测试 8: 成功捕获预期异常，结束 ----------\n")
    }

    /**
     * 测试场景 9: 验证一个并行任务能正确地等待一个混合的依赖列表（包含串行和并行任务）。
     */
    @Test
    fun `9 - 混合依赖测试`() = runTest {
        println("---------- 测试 9: 混合依赖 开始 ----------")
        val s1 = S1
        val pa = PA
        val pMixC = P_MixC
        var completed = false

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(s1, pa, pMixC),
            onCompletion = {
                println("测试 9: onCompletion 回调被触发")
                completed = true
            },
            onError = { throw it.first() }
        )

        println("测试 9: 调用 startup.start()")
        startup.start()
        println("测试 9: 调用 advanceUntilIdle() 来执行所有排队任务...")
        advanceUntilIdle()
        println("测试 9: advanceUntilIdle() 执行完毕")

        println("测试 9: 开始断言...")
        assertTrue(completed)
        assertEquals(1, s1.callCount.get())
        assertEquals(1, pa.callCount.get())
        assertEquals(1, pMixC.callCount.get())
        assertTrue(
            "P_MixC 应该在 S1 和 PA 之后执行",
            pMixC.executedAt >= s1.executedAt && pMixC.executedAt >= pa.executedAt
        )
        println("---------- 测试 9: 成功结束 ----------\n")
    }

    /**
     * 测试场景 10: 验证当一个并行任务失败时，依赖于它的其他任务不会被执行（异常传播）。
     */
    @Test
    fun `10 - 并行任务异常传播测试`() = runTest {
        println("---------- 测试 10: 并行任务异常传播 开始 ----------")
        var capturedErrors: List<Throwable>? = null
        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(FailingParallelA, DependentOnFailure),
            onCompletion = { fail("onCompletion 不应被调用") },
            onError = {
                println("测试 10: onError 回调被触发，错误数量: ${it.size}")
                capturedErrors = it
            }
        )

        println("测试 10: 调用 startup.start()")
        startup.start()
        println("测试 10: 调用 advanceUntilIdle() 来执行任务并触发回调...")
        advanceUntilIdle()
        println("测试 10: advanceUntilIdle() 执行完毕")

        println("测试 10: 开始断言...")
        // 依赖失败任务的任务不应被执行。
        assertEquals(0, DependentOnFailure.callCount.get())
        Assert.assertNotNull(capturedErrors)
        // 验证原始异常被正确捕获和报告。
        val hasOriginalException =
            capturedErrors!!.any { it.message?.contains("FailingParallelA failed!") == true }
        assertTrue("错误列表应包含原始异常", hasOriginalException)
        println("---------- 测试 10: 成功结束 ----------\n")
    }

    /**
     * 测试场景 11: 验证调用 `startup.cancel()` 能正确地取消正在进行的启动流程，并触发 `onError` 回调。
     * 这是对框架取消逻辑和`finally`块中回调机制的终极考验。
     */
    @Test
    fun `11 - 取消操作测试`() = runTest {
        println("---------- 测试 11: 取消操作 开始 ----------")
        val cancellableTask = CancellableInitializer()
        var capturedErrors: List<Throwable>? = null

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(cancellableTask),
            onCompletion = { fail("onCompletion 不应被调用") },
            onError = {
                println("测试 11: onError 回调被触发，错误: ${it.firstOrNull()}")
                capturedErrors = it
            }
        )

        // 1. 开始。任务被排队，但尚未执行。
        println("测试 11: 调用 startup.start()")
        startup.start()

        // 2. 运行当前排队的任务。`cancellableTask` 将启动并因 `await()` 而挂起。
        println("测试 11: 调用 runCurrent() 来启动并挂起任务...")
        runCurrent()

        // 3. 此刻，任务已确认在挂起状态，现在取消整个流程。
        //    这个取消动作会将 `onError` 回调排队到调度器上。
        println("测试 11: 调用 startup.cancel()")
        startup.cancel()

        // 4. 执行所有剩余的排队任务，这其中必然包括了被排队的 `onError` 回调。
        println("测试 11: 调用 advanceUntilIdle() 以执行取消和回调...")
        advanceUntilIdle()
        println("测试 11: advanceUntilIdle() 执行完毕")

        // 5. 现在可以安全地进行断言，因为所有调度都已经完成。
        println("测试 11: 开始断言...")
        assertEquals(1, cancellableTask.callCount.get()) // 任务被启动过
        Assert.assertNotNull("取消后 capturedErrors 不应为 null", capturedErrors)
        assertTrue(
            "错误列表应包含 CancellationException",
            capturedErrors!!.any { it is CancellationException }
        )
        println("---------- 测试 11: 成功结束 ----------\n")
    }


    /**
     * 测试场景 12: 验证在一个并行任务组中，某个任务的失败不会影响其他无依赖关系的并行任务的执行。
     * 这是 `supervisorScope` 功能的核心测试。
     */
    @Test
    fun `12 - 并行任务单个异常不影响其他任务并行测试`() = runTest {
        println("---------- 测试 12: 并行任务异常隔离 开始 ----------")
        val failingTask = FailingParallelA
        val normalTask = NormalParallelB
        var capturedErrors: List<Throwable>? = null

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(),
            initializers = listOf(failingTask, normalTask),
            onCompletion = { fail("onCompletion 不应被调用") },
            onError = {
                println("测试 12: onError 回调被触发，错误数量: ${it.size}")
                capturedErrors = it
            }
        )

        println("测试 12: 调用 startup.start()")
        startup.start()
        println("测试 12: 调用 advanceUntilIdle() 来执行所有任务...")
        advanceUntilIdle()
        println("测试 12: advanceUntilIdle() 执行完毕")


        println("测试 12: 开始断言...")
        // 失败的任务被调用了。
        assertEquals(1, failingTask.callCount.get())
        // 正常的并行任务也应该被调用并完成。
        assertEquals(1, normalTask.callCount.get())
        // onError 回调只收到了失败任务的异常。
        assertEquals(1, capturedErrors?.size)
        assertTrue(capturedErrors?.first()?.message?.contains("FailingParallelA failed!") == true)
        println("---------- 测试 12: 成功结束 ----------\n")
    }

    /**
     * 测试场景 13: 验证在 Initializer 内部使用 `withContext` 切换到不同调度器（如 IO）时，
     * 框架能正确等待其完成，并能让其他任务获取其结果。
     */
    @Test
    fun `13 - 任务主动切换线程测试`() = runTest {
        println("---------- 测试 13: 任务中切换线程 开始 ----------")
        // 创建一个特殊的 Initializer，并注入 IO 测试调度器
        val switcher = ThreadSwitchingInitializer(newDispatcher = ioTestDispatcher)
        var resultFromSwitcher: String? = null
        var completed = false

        // 创建另一个 Initializer，它依赖于 switcher 并将获取其结果。
        val resultReader = ResultInitializer("ResultReader") { _, provider ->
            println("测试 13: resultReader 开始执行，尝试获取 'switcher' 的结果")
            resultFromSwitcher = provider.resultOrNull<String>(ThreadSwitchingInitializer::class)
            "ResultReaderDone"
        }.apply {
            dependencies = listOf(ThreadSwitchingInitializer::class)
        }

        val startup = Startup(
            context = mockContext,
            dispatchers = createTestDispatchers(execute = mainDispatcherRule.testDispatcher),
            initializers = listOf(switcher, resultReader),
            onCompletion = {
                println("测试 13: onCompletion 回调被触发")
                completed = true
            },
            onError = { fail("onError 不应被调用: ${it.firstOrNull()?.message}") }
        )

        println("测试 13: 调用 startup.start()")
        startup.start()
        // 执行所有任务，包括在 ioTestDispatcher 上排队的 withContext 块。
        println("测试 13: 调用 advanceUntilIdle() 来执行所有任务（包括线程切换）...")
        advanceUntilIdle()
        println("测试 13: advanceUntilIdle() 执行完毕")

        println("测试 13: 开始断言...")
        assertTrue(completed)
        assertEquals(1, switcher.callCount.get())
        Assert.assertNotNull(resultFromSwitcher)
        // 验证线程确实发生了切换（从测试主线程开始）。
        assertTrue(resultFromSwitcher!!.contains("InitialThread: TestMain"))
        println("线程切换结果：$resultFromSwitcher")
        println("---------- 测试 13: 成功结束 ----------\n")
    }
}

/**
 * 一个 JUnit 规则，用于在测试中可靠地替换和重置 `Dispatchers.Main`。
 * 这是测试任何与主线程相关的协程代码的标准实践。
 *
 * @param testDispatcher 要用于替换 `Dispatchers.Main` 的测试调度器。
 *                       **关键实践**: 默认为 `StandardTestDispatcher`，以保证所有测试的可预测性。
 */
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
        // 为测试主线程命名，便于在日志中识别。
        Thread.currentThread().name = "TestMain"
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        Thread.currentThread().name = "main" // 恢复原始线程名
    }
}
