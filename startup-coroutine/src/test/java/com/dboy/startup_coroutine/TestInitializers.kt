package com.dboy.startup_coroutine

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * 测试专用的 Initializer 基础类，提供了调用计数和执行时间记录等通用功能。
 *
 * @param T 返回值的类型。
 * @param action 在 init 方法中执行的挂起 lambda，用于注入自定义测试逻辑。
 * @param dependencies 声明此任务的依赖项。
 * @param mode 声明此任务的执行模式（SERIAL 或 PARALLEL）。
 */
open class BaseTestInitializer<T>(
    val name: String,
    var action: (suspend (Context, DependenciesProvider) -> T)? = null,
    var dependencies: List<KClass<out Initializer<*>>> = emptyList(),
    private val mode: InitMode = InitMode.SERIAL // 默认串行
) : Initializer<T>() {

    // 记录 init 方法被调用的次数
    val callCount = AtomicInteger(0)

    // 记录 init 方法被调用的时间戳 (毫秒)
    @Volatile
    var executedAt: Long = 0L

    override suspend fun init(context: Context, provider: DependenciesProvider): T {
        println()
        println("任务$name 开始执行")
        executedAt = System.currentTimeMillis()
        callCount.incrementAndGet()
        @Suppress("UNCHECKED_CAST")
        return action?.invoke(context, provider) ?: Unit as T
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> = dependencies

    override fun initMode(): InitMode = mode

    override fun toString(): String {
        return "TestInitializer(name='$name', mode=$mode)"
    }
}

// =====================================================================================
// === 实现类，用于满足 StartupTest.kt 中的 12 个核心测试场景 ==========================
// =====================================================================================

// --- 场景 1 & 2: 串行任务依赖 ---
object S1 : BaseTestInitializer<Unit>("S1", mode = InitMode.SERIAL)
object S2 :
    BaseTestInitializer<Unit>("S2", dependencies = listOf(S1::class), mode = InitMode.SERIAL)

object S3 : BaseTestInitializer<Unit>(
    "S3",
    dependencies = listOf(S1::class, S2::class),
    mode = InitMode.SERIAL
)

// --- 场景 3 & 4: 并行任务依赖串行任务 ---
object P1 :
    BaseTestInitializer<Unit>("P1", dependencies = listOf(S1::class), mode = InitMode.PARALLEL)

object P2 : BaseTestInitializer<Unit>(
    "P2",
    dependencies = listOf(S1::class, S2::class),
    mode = InitMode.PARALLEL
)

// --- 场景 5 & 6: 并行任务依赖并行任务 ---
object PA : BaseTestInitializer<Unit>("PA", mode = InitMode.PARALLEL)
object PB :
    BaseTestInitializer<Unit>("PB", dependencies = listOf(PA::class), mode = InitMode.PARALLEL)

object PC :
    BaseTestInitializer<Unit>("PC", dependencies = listOf(PA::class), mode = InitMode.PARALLEL)

object PD : BaseTestInitializer<Unit>(
    "PD",
    dependencies = listOf(PB::class, PC::class),
    mode = InitMode.PARALLEL
)

// --- 场景 7: 循环依赖 ---
object CycleA : BaseTestInitializer<Unit>(
    "CycleA",
    dependencies = listOf(CycleC::class),
    mode = InitMode.PARALLEL
)

object CycleB : BaseTestInitializer<Unit>(
    "CycleB",
    dependencies = listOf(CycleA::class),
    mode = InitMode.PARALLEL
)

object CycleC : BaseTestInitializer<Unit>(
    "CycleC",
    dependencies = listOf(CycleB::class),
    mode = InitMode.PARALLEL
)

// --- 场景 8: 串行任务非法依赖并行任务 ---
object IllegalDepSerial : BaseTestInitializer<Unit>(
    "IllegalDepSerial",
    dependencies = listOf(PA::class),
    mode = InitMode.SERIAL
)

// --- 场景 9: 混合依赖 (P_MixC 依赖串行 S1 和并行 PA) ---
object P_MixC : BaseTestInitializer<Unit>(
    "P_MixC",
    dependencies = listOf(S1::class, PA::class),
    mode = InitMode.PARALLEL
)

// --- 场景 10 & 12: 异常处理 ---
// 并行任务，初始化会失败
object FailingParallelA :
    BaseTestInitializer<Unit>("FailingParallelA", mode = InitMode.PARALLEL, action = { _, _ ->
        throw RuntimeException("FailingParallelA failed!")
    })

// 依赖于失败任务的并行任务
object DependentOnFailure : BaseTestInitializer<Unit>(
    "DependentOnFailure",
    dependencies = listOf(FailingParallelA::class),
    mode = InitMode.PARALLEL
)

// 正常的并行任务，用于验证其是否受影响
object NormalParallelB : BaseTestInitializer<Unit>("NormalParallelB", mode = InitMode.PARALLEL)

class ThreadSwitchingInitializer : BaseTestInitializer<String>(
    name = "ThreadSwitcher",
    mode = InitMode.PARALLEL,
) {
    var dispatcherProvider: () -> CoroutineDispatcher = { Dispatchers.IO }

    override suspend fun init(context: Context, provider: DependenciesProvider): String {
        // 调用父类方法来记录时间和调用次数
        super.init(context, provider)

        val initialThreadName = Thread.currentThread().name

        val result = withContext(dispatcherProvider()) {
            val ioThreadName = Thread.currentThread().name
            "InitialThread: $initialThreadName, IOThread: $ioThreadName"
        }

        return result
    }
}

class ResultInitializer(
    private val result: String,
    action: (suspend (Context, DependenciesProvider) -> String)? = { _, _ -> result },
) :
    BaseTestInitializer<String>(
        name = "ResultInitializer",
        mode = InitMode.PARALLEL,
        action = action
    )

class CancellableInitializer : BaseTestInitializer<Unit>(
    name = "Cancellable",
    mode = InitMode.SERIAL
) {
    private val completable = CompletableDeferred<Unit>()

    override suspend fun init(context: Context, provider: DependenciesProvider): Unit {
        super.init(context, provider)
        completable.await()
    }

    fun complete() = completable.complete(Unit)
}