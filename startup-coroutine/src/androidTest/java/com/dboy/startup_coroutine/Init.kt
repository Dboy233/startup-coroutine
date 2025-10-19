package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.delay

// A -> (无依赖, 并行)
class InitializerA : Initializer<String>() {
    override suspend fun init(context: Context, dispatcher: ResultDispatcher): String {
        println("InitializerA: start")
        delay(300)
        println("InitializerA: end")
        return "ResultA"
    }
}

// B -> (无依赖, 串行)
class InitializerB : Initializer<String>() {
    override fun initMode() = InitMode.SERIAL
    override suspend fun init(context: Context, dispatcher: ResultDispatcher): String {
        println("InitializerB: start")
        delay(200)
        println("InitializerB: end")
        return "ResultB"
    }
}

// C -> (依赖 A, 并行)
class InitializerC : Initializer<String>() {
    override fun dependencies() = listOf(InitializerA::class.java)
    override suspend fun init(context: Context, dispatcher: ResultDispatcher): String {
        val resultA = dispatcher.getResult(InitializerA::class.java)
        println("InitializerC: start, got '$resultA'")
        delay(100)
        println("InitializerC: end")
        return "ResultC"
    }
}

// D -> (依赖 B, 并行)
class InitializerD : Initializer<String>() {
    override fun dependencies() = listOf(InitializerB::class.java)
    override suspend fun init(context: Context, dispatcher: ResultDispatcher): String {
        val resultB = dispatcher.getResult(InitializerB::class.java)
        println("InitializerD: start, got '$resultB'")
        delay(50)
        println("InitializerD: end")
        return "ResultD"
    }
}

// E -> (依赖 C 和 D, 并行)
class InitializerE : Initializer<String>() {
    override fun dependencies() = listOf(InitializerC::class.java, InitializerD::class.java)
    override suspend fun init(context: Context, dispatcher: ResultDispatcher): String {
        val resultC = dispatcher.getResult(InitializerC::class.java)
        val resultD = dispatcher.getResult(InitializerD::class.java)
        println("InitializerE: start, got '$resultC' and '$resultD'")
        delay(20)
        println("InitializerE: end")
        return "ResultE"
    }
}

// F -> (非法依赖: 串行依赖并行A, 用于测试校验)
class InvalidInitializerF : Initializer<Unit>() {
    override fun initMode() = InitMode.SERIAL
    override fun dependencies() = listOf(InitializerA::class.java)
    override suspend fun init(context: Context, dispatcher: ResultDispatcher) {
        // This should never be called
    }
}

// G -> (循环依赖 H, 用于测试校验)
class CircularInitializerG : Initializer<Unit>() {
    override fun dependencies() = listOf(CircularInitializerH::class.java)
    override suspend fun init(context: Context, dispatcher: ResultDispatcher) {}
}

// H -> (循环依赖 G, 用于测试校验)
class CircularInitializerH : Initializer<Unit>() {
    override fun dependencies() = listOf(CircularInitializerG::class.java)
    override suspend fun init(context: Context, dispatcher: ResultDispatcher) {}
}
