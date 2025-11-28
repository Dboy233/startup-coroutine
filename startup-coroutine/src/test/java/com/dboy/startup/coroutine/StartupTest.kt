package com.dboy.startup.coroutine

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import com.dboy.startup.coroutine.model.StartupResult
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class StartupTest {
    // 2. 应用协程规则，接管 Main 线程
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockLog: Log

    @Mock
    private lateinit var mockApplication: Application

    private fun createTestDispatchers(
        start: TestDispatcher = mainDispatcherRule.testDispatcher,
        execute: TestDispatcher = mainDispatcherRule.testDispatcher,
    ) = StartupDispatchers(start, execute)

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
    }

    @Test
    fun `test 5 simulated tasks dependency flow`() = kotlinx.coroutines.test.runTest {
        // 1. 准备任务实例
        val preInit = PreInit()
        val networkConfigInit = NetworkConfigInit()
        val databaseInit = DatabaseInit()
        val adsInit = AdsInit()
        val otherInit = OtherInit()

        // 2. 构建 Startup，注意添加顺序可以是乱序的，框架应该自己理清依赖
        val startup = Startup.Builder(mockApplication)
            .setDebug(true) // 开启 Debug 模式以查看日志（配合 build.gradle 配置）
            .setDispatchers(createTestDispatchers())
            .add(otherInit) // 故意乱序添加
            .add(adsInit)
            .add(databaseInit)
            .add(networkConfigInit)
            .add(preInit)
            .build()

        val testOwner = TestLifecycleOwner()
        testOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        var capturedResult: StartupResult? = null

        Startup.observe(testOwner) { result ->
            capturedResult = result
            println("Observed result: $result")
        }


        startup.start()

        advanceUntilIdle()

        Assert.assertTrue(capturedResult is StartupResult.Success)

        // 9. 模拟销毁（可选测试）
        testOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

    }


    @Test
    fun `test circular dependency detection`() = runTest {
        // 1. 准备循环依赖的任务 (A 依赖 B, B 依赖 A)
        val initA = CircularDependencyInitA()
        val initB = CircularDependencyInitB()

        val startup = Startup.Builder(mockApplication)
            .setDebug(true)
            .setDispatchers(createTestDispatchers())
            .add(initA)
            .add(initB)
            .build()

        val testOwner = TestLifecycleOwner()
        testOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        var capturedResult: StartupResult? = null
        Startup.observe(testOwner) { result ->
            capturedResult = result
            println("Circular dependency result: ${(result as? StartupResult.Failure)?.exceptions}")
        }

        startup.start()
        advanceUntilIdle()

        // 验证: 循环依赖通常会导致死锁超时或栈溢出，框架应该捕获并返回 Failure
        Assert.assertTrue(
            "循环依赖应该导致 StartupResult.Failure",
            capturedResult is StartupResult.Failure
        )
    }

    @Test
    fun `test marginal task failure handling`() = runTest {
        // 1. 准备一个会抛出异常的任务
        val marginalInit = MarginalTaskInit()

        val preInit = PreInit()
        val networkConfigInit = NetworkConfigInit()
        val databaseInit = DatabaseInit()
        val adsInit = AdsInit()
        val otherInit = OtherInit()

        val startup = Startup.Builder(mockApplication)
            .setDebug(true)
            .setDispatchers(createTestDispatchers())
            .add(marginalInit)
            .add(otherInit) // 故意乱序添加
            .add(adsInit)
            .add(databaseInit)
            .add(networkConfigInit)
            .add(preInit)
            .build()

        val testOwner = TestLifecycleOwner()
        testOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        var capturedResult: StartupResult? = null
        Startup.observe(testOwner) { result ->
            capturedResult = result
            println("Marginal task failure result: ${(result as? StartupResult.Failure)?.exceptions}")
        }

        startup.start()
        advanceUntilIdle()

        // 验证: 任何任务抛出未捕获异常，通常会导致整体 Failure
        Assert.assertTrue(
            "任务抛出异常应该导致 StartupResult.Failure",
            capturedResult is StartupResult.Failure
        )

        val failure = capturedResult as StartupResult.Failure
        // 验证异常信息是否传递出来了
        Assert.assertTrue(
            "Failure 中应包含具体的异常信息",
            failure.exceptions.first().exception.message?.contains("边缘任务初始化失败了") == true
        )
    }

}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        Dispatchers.resetMain()
    }
}