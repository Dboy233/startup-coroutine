//package com.dboy.startup_coroutine
//
//import android.content.Context
//import com.google.common.truth.Truth.assertThat
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.channels.Channel
//import org.junit.Assert.assertThrows
//import org.junit.Before
//import org.junit.Test
//
//@OptIn(ExperimentalCoroutinesApi::class)
//class StartupTest {
//
//    private lateinit var mockContext: Context
//
//    @Before
//    fun setup() {
//        // 使用 Mockito 创建一个模拟的 Context 对象，因为我们的框架需要它
//        mockContext = mock(Context::class.java)
//    }
//
//    @Test
//    fun `正常执行 - 所有任务完成并调用 onCompletion 回调`() {
//        // 使用 Channel 来同步测试线程和协程的完成状态
//        val completionChannel = Channel<Boolean>(1)
//
//        val initializers = listOf(
//            InitializerA(),
//            InitializerB(),
//            InitializerC(),
//            InitializerD(),
//            InitializerE()
//        )
//
//        val startup = Startup(
//            context = mockContext,
//            initializers = initializers,
//            onCompletion = {
//                println("Test: All initializers completed!")
//                // 当 onCompletion 被调用时，向 Channel 发送一个信号
//                completionChannel.trySend(true)
//            }
//        )
//
//        startup.start()
//
//        // 等待 onCompletion 被调用，设置超时以防测试卡死
//        val completed = completionChannel.receive()
//
//        assertThat(completed).isTrue()
//    }
//
//    @Test
//    fun `校验失败 - 串行任务依赖并行任务应抛出异常`() {
//        val initializers = listOf(
//            InitializerA(), // 并行
//            InvalidInitializerF() // 串行，依赖 A
//        )
//
//        // 断言 Startup 的构造或启动会因为非法依赖而抛出 IllegalStateException
//        val exception = assertThrows(IllegalStateException::class.java) {
//            // 我们可以在 runTest 中直接运行它来捕获异常
//            runTest {
//                Startup(
//                    context = mockContext,
//                    initializers = initializers,
//                    onCompletion = { /* 不会执行 */ }
//                ).start()
//            }
//        }
//
//        // 验证异常信息是否符合预期
//        println("Caught expected exception: ${exception.message}")
//        assertThat(exception.message).contains(
//            "Serial initializer 'InvalidInitializerF' cannot depend on Parallel initializer 'InitializerA'"
//        )
//    }
//
//    @Test
//    fun `校验失败 - 循环依赖应抛出异常`() {
//        val initializers = listOf(
//            CircularInitializerG(),
//            CircularInitializerH()
//        )
//
//        val exception = assertThrows(IllegalStateException::class.java) {
//            runTest {
//                Startup(
//                    context = mockContext,
//                    initializers = initializers,
//                    onCompletion = { /* 不会执行 */ }
//                ).start()
//            }
//        }
//
//        println("Caught expected exception: ${exception.message}")
//        assertThat(exception.message).contains("Circular dependency detected")
//    }
//
//    @Test
//    fun `执行顺序 - 并行任务D必须在串行任务B之后执行`() = runTest {
//        val executionLog = mutableListOf<String>()
//        val completionChannel = Channel<Boolean>(1)
//
//        // 自定义 Initializer 来记录执行顺序
//        class LoggingB : Initializer<Unit>() {
//            override fun initMode() = InitMode.SERIAL
//            override suspend fun init(context: Context, dispatcher: ResultDispatcher) {
//                executionLog.add("B_start")
//                delay(100)
//                executionLog.add("B_end")
//            }
//        }
//
//        class LoggingD : Initializer<Unit>() {
//            override fun dependencies() = listOf(LoggingB::class.java)
//            override suspend fun init(context: Context, dispatcher: ResultDispatcher) {
//                executionLog.add("D_start")
//                delay(10)
//                executionLog.add("D_end")
//            }
//        }
//
//        Startup(
//            context = mockContext,
//            initializers = listOf(LoggingB(), LoggingD()),
//            onCompletion = { completionChannel.trySend(true) }
//        ).start()
//
//        completionChannel.receive() // 等待所有任务完成
//
//        // 断言 B_end 的日志记录在 D_start 之前
//        assertThat(executionLog).containsExactly("B_start", "B_end", "D_start", "D_end").inOrder()
//        val indexOfBEnd = executionLog.indexOf("B_end")
//        val indexOfDStart = executionLog.indexOf("D_start")
//        assertThat(indexOfBEnd).isLessThan(indexOfDStart)
//    }
//}
