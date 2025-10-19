package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.delay

// 一个简单的日志记录器，用于在测试中断言执行顺序
object TestLog {
    private val log = mutableListOf<String>()

    fun add(entry: String) {
        synchronized(this) {            log.add(entry)
            println("TestLog: $entry") // 在控制台打印日志，便于调试
        }
    }

    fun get(): List<String> {
        synchronized(this) {
            return ArrayList(log)
        }
    }

    fun clear() {
        synchronized(this) {
            log.clear()
        }
    }
}


// --- 用于新测试的 Initializer ---

// 并行任务 X (无依赖)
class ParallelX : Initializer<String>() {
    override suspend fun init(context: Context, dispatcher: ResultDispatcher): String {
        TestLog.add("ParallelX: start")
        delay(200) // 耗时较长
        TestLog.add("ParallelX: end")
        return "ResultX"
    }
}

// 并行任务 Y, 依赖 X
class ParallelY : Initializer<String>() {
    override fun dependencies() = listOf(ParallelX::class.java)
    override suspend fun init(context: Context, dispatcher: ResultDispatcher): String {
        TestLog.add("ParallelY: start, got '${dispatcher.getResult(ParallelX::class.java)}'")
        delay(50)
        TestLog.add("ParallelY: end")
        return "ResultY"
    }
}

// 并行任务 Z, 依赖 X
class ParallelZ : Initializer<String>() {
    override fun dependencies() = listOf(ParallelX::class.java)
    override suspend fun init(context: Context, dispatcher: ResultDispatcher): String {
        TestLog.add("ParallelZ: start, got '${dispatcher.getResult(ParallelX::class.java)}'")
        delay(30)
        TestLog.add("ParallelZ: end")
        return "ResultZ"
    }
}

// 任务 W, 依赖 Y 和 Z
class ParallelW : Initializer<String>() {
    override fun dependencies() = listOf(ParallelY::class.java, ParallelZ::class.java)
    override suspend fun init(context: Context, dispatcher: ResultDispatcher): String {
        TestLog.add("ParallelW: start, got '${dispatcher.getResult(ParallelY::class.java)}' and '${dispatcher.getResult(ParallelZ::class.java)}'")
        TestLog.add("ParallelW: end")
        return "ResultW"
    }
}

// 一个会抛出异常的任务
class FailingInitializer : Initializer<Unit>() {
    override suspend fun init(context: Context, dispatcher: ResultDispatcher) {
        TestLog.add("FailingInitializer: start")
        delay(50)
        throw RuntimeException("FailingInitializer failed intentionally!")
    }
}
