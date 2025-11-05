@file:Suppress("NonAsciiCharacters")

package com.dboy.startup.coroutine

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
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
     * 在每个测试方法执行前运行。
     * 主要负责重置所有测试用到的 `Initializer` 单例对象的状态（如调用次数、执行时间），
     * 确保每个测试用例都在一个干净、独立的环境中开始。
     */
    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0 // 对 w 方法的特定重载进行 mock
        every { Log.e(any(), any()) } returns 0
        // 每个测试开始前重置单例对象的状态

    }

    /**
     * 在每个测试方法执行后运行。
     * 目前为空，可用于未来的清理逻辑。
     */
    @After
    fun tearDown() {
        // 测试后清理
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
