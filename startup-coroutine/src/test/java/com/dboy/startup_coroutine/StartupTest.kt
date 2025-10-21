// StartupTest.kt
package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class) // 启用实验性的测试API
class StartupTest {

    // 1. 创建一个测试调度器
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    // 2. 使用 @get:Rule 来自动设置和重置 Main dispatcher
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

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

    // 辅助函数，现在改为 suspend fun，并且不再需要 CountDownLatch
    private suspend fun runTest(
        initializers: List<Initializer<*>>,
        onCompletion: () -> Unit,
        onError: ((List<Throwable>) -> Unit)? = null
    ) {
        suspendCancellableCoroutine<Unit>{
            val startup = Startup(
                context = mockContext,
                initializers = initializers,
                onCompletion = {
                    onCompletion()
                    it.resume(Unit)
                },
                onError = { e->
                    onError?.invoke(e)
                    it.resume(Unit)
                }
            )
            startup.start()
            // runTest 会自动等待 startup.start() 启动的协程完成
        }
    }

    @Test
    fun `1 - 串行任务的单一依赖`() = runTest {
        var completed = false
        runTest(listOf(SerialTaskA(), SerialTaskB()), onCompletion = {
            completed = true
            assertEquals(2, logList.size)
            assertTrue(logList[0].startsWith("SerialTaskA"))
            assertTrue(logList[1].startsWith("SerialTaskB"))
            val threadA = logList[0].substringAfter("on ")
            val threadB = logList[1].substringAfter("on ")
            assertEquals(threadA, threadB)
        }, onError = {
            it.forEach { e->
                println(e)
            }

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
        })
        assertTrue(completed)
    }

    @Test
    fun `3 - 并行任务依赖单个串行任务`() = runTest {
        runTest(listOf(SerialTaskA(), ParallelTaskD()), onCompletion = {
            assertEquals(2, logList.size)
            assertEquals("SerialTaskA", logList[0].substringBefore(" on"))
            assertEquals("ParallelTaskD", logList[1].substringBefore(" on"))
            assertTrue(logList[1].contains("DefaultDispatcher"))
        })
    }

    @Test
    fun `4 - 并行任务依赖多个串行任务`() = runTest {
        runTest(listOf(SerialTaskA(), SerialTaskB(), ParallelTaskE()), onCompletion = {
            assertEquals(3, logList.size)
            val taskNames = logList.map { it.substringBefore(" on") }
            assertTrue(taskNames.indexOf("SerialTaskA") < taskNames.indexOf("ParallelTaskE"))
            assertTrue(taskNames.indexOf("SerialTaskB") < taskNames.indexOf("ParallelTaskE"))
        })
    }

    @Test
    fun `5 - 并行任务依赖单个并行任务`() = runTest {
        runTest(listOf(ParallelTaskF(), ParallelTaskG()), onCompletion = {
            assertEquals(2, logList.size)
            val taskNames = logList.map { it.substringBefore(" on") }
            assertTrue(taskNames.indexOf("ParallelTaskF") < taskNames.indexOf("ParallelTaskG"))
            assertTrue(logList[0].contains("DefaultDispatcher"))
            assertTrue(logList[1].contains("DefaultDispatcher"))
        })
    }

    @Test
    fun `6 - 并行任务依赖多个并行任务`() = runTest {
        runTest(listOf(ParallelTaskF(), ParallelTaskG(), ParallelTaskH()), onCompletion = {
            assertEquals(3, logList.size)
            val taskNames = logList.map { it.substringBefore(" on") }
            assertTrue(taskNames.indexOf("ParallelTaskF") < taskNames.indexOf("ParallelTaskH"))
            assertTrue(taskNames.indexOf("ParallelTaskG") < taskNames.indexOf("ParallelTaskH"))
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
        runTest(listOf(SerialTaskA(), SerialTaskB(), ParallelTaskF(), ParallelTaskG(), MixedDependencyTaskL()), onCompletion = {
            assertEquals(5, logList.size)
            val taskNames = logList.map { it.substringBefore(" on") }
            assertTrue(taskNames.indexOf("SerialTaskA") < taskNames.indexOf("MixedDependencyTaskL"))
            assertTrue(taskNames.indexOf("SerialTaskB") < taskNames.indexOf("MixedDependencyTaskL"))
            assertTrue(taskNames.indexOf("ParallelTaskF") < taskNames.indexOf("MixedDependencyTaskL"))
            assertTrue(taskNames.indexOf("ParallelTaskG") < taskNames.indexOf("MixedDependencyTaskL"))
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
        assertEquals("M task failed deliberately!", caughtExceptions!!.first().message)
        assertTrue(logList.any { it.startsWith("ExceptionTaskM_Start") })
        assertFalse(logList.any { it.startsWith("DependentOnExceptionTaskN") })
    }

    @Test
    fun `11 - 取消操作测试`() = runTest {
        // 使用 CompletableDeferred 来确保回调被调用
        val errorCompletable = CompletableDeferred<List<Throwable>>()
        val initializerList = listOf(SerialTaskA(), SerialTaskB(), ParallelTaskF(), ParallelTaskG())
        val startup = Startup(
            context = mockContext,
            initializers = initializerList,
            onCompletion = { fail("Should have been cancelled") },
            onError = {
                // 当 onError 被调用时，完成 CompletableDeferred
                errorCompletable.complete(it)
            }
        )
        startup.start()
        // 稍微延迟一下，确保 start() 里的协程有机会启动
        delay(10)
        startup.cancel()

        // 等待 onError 回调，设置超时
        val caughtExceptions = withContext(Dispatchers.Default) {
            withTimeout(2000) {
                errorCompletable.await()
            }
        }

        // 现在进行正确的断言
        assertNotNull(caughtExceptions)
        // 验证捕获的异常中至少有一个是 CancellationException
        assertTrue(caughtExceptions.any { it is CancellationException })
        // 任务可能执行了部分，但绝不会全部执行完
        assertTrue(logList.size < initializerList.size)
    }
}

// 辅助类，用于自动设置和清理 Main dispatcher
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : org.junit.rules.TestWatcher() {
    override fun starting(description: org.junit.runner.Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: org.junit.runner.Description) {
        Dispatchers.resetMain()
    }
}
