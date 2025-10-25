// TestInitializers.kt
package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.util.Collections
import kotlin.reflect.KClass

// 用于在测试中同步记录执行日志，线程安全
val logList = Collections.synchronizedList(mutableListOf<String>())

// 辅助函数：记录日志，包含任务名和当前线程名
suspend fun log(taskName: String) {
    logList.add("$taskName on ${Thread.currentThread().name}")
    // 模拟耗时操作
    delay(50)
}

// --- 串行任务 ---
class SerialTaskA : Initializer<String>() {
    override suspend fun init(context: Context, provider: DependenciesProvider): String {
        log("SerialTaskA")
        return "ResultA"
    }
}

class SerialTaskB : Initializer<String>() {
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(SerialTaskA::class)
    override suspend fun init(context: Context, provider: DependenciesProvider): String {
        log("SerialTaskB")
        // 测试获取依赖结果
        val resultA = provider.result<String>(SerialTaskA::class)
        assert(resultA == "ResultA")
        return "ResultB"
    }
}

class SerialTaskC : Initializer<Unit>() {
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(SerialTaskA::class, SerialTaskB::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) {
        log("SerialTaskC")
    }
}

// --- 并行任务 ---
class ParallelTaskD : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(SerialTaskA::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) {
        log("ParallelTaskD")
    }
}

class ParallelTaskE : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(SerialTaskA::class, SerialTaskB::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) {
        log("ParallelTaskE")
    }
}

class ParallelTaskF : Initializer<String>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override suspend fun init(context: Context, provider: DependenciesProvider): String {
        log("ParallelTaskF")
        return "ResultF"
    }
}

class ParallelTaskG : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(ParallelTaskF::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) {
        log("ParallelTaskG")
        val resultF = provider.result<String>(ParallelTaskF::class)
        assert(resultF == "ResultF")
    }
}

class ParallelTaskH : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(ParallelTaskF::class, ParallelTaskG::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) {
        log("ParallelTaskH")
    }
}

// --- 用于异常和依赖测试的特殊任务 ---

// 循环依赖: I -> J -> I
class CircularTaskI : Initializer<Unit>() {
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(CircularTaskJ::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) { log("CircularTaskI") }
}

class CircularTaskJ : Initializer<Unit>() {
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(CircularTaskI::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) { log("CircularTaskJ") }
}

// 串行依赖并行 (非法)
class InvalidDependencyTaskK : Initializer<Unit>() {
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(ParallelTaskF::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) { log("InvalidDependencyTaskK") }
}

// 混合依赖任务
class MixedDependencyTaskL : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(SerialTaskA::class, SerialTaskB::class, ParallelTaskF::class, ParallelTaskG::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) {
        log("MixedDependencyTaskL")
    }
}

// 会抛出异常的任务
class ExceptionTaskM : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override suspend fun init(context: Context, provider: DependenciesProvider) {
        log("ExceptionTaskM_Start")
        throw RuntimeException("M task failed deliberately!")
    }
}

// 依赖于会抛出异常的任务
class DependentOnExceptionTaskN : Initializer<Unit>() {
    override fun initMode(): InitMode = InitMode.PARALLEL
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(ExceptionTaskM::class)
    override suspend fun init(context: Context, provider: DependenciesProvider) {
        // 这个任务不应该被执行
        log("DependentOnExceptionTaskN")
    }
}
