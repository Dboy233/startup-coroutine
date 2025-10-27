package com.dboy.startup.coroutine

import android.content.Context
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.InitMode
import com.dboy.startup.coroutine.api.Initializer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * 提供一系列用于单元测试的 [com.dboy.startup.coroutine.api.Initializer] 实现类。
 *
 * 这些类被设计用来模拟各种启动场景，包括不同的依赖关系、执行模式、成功、失败、
 * 耗时操作、线程切换以及取消等，以全面验证 [Startup] 框架的正确性和健壮性。
 */

/**
 * 测试专用的 Initializer 基础类，提供了调用计数、执行时间记录和日志打印等通用功能。
 *
 * @param name 任务的唯一名称，用于日志输出和识别。
 * @param T 返回值的类型。
 * @param action 在 init 方法中执行的挂起 lambda，用于注入自定义测试逻辑，如抛出异常或返回特定值。
 * @param dependencies 声明此任务的依赖项列表。
 * @param mode 声明此任务的执行模式 ([com.dboy.startup.coroutine.api.InitMode.SERIAL] 或 [com.dboy.startup.coroutine.api.InitMode.PARALLEL])。
 */
open class BaseTestInitializer<T>(
    val name: String,
    var action: (suspend (Context, DependenciesProvider) -> T)? = null,
    var dependencies: List<KClass<out Initializer<*>>> = emptyList(),
    private val mode: InitMode = InitMode.SERIAL // 默认串行
) : Initializer<T>() {

    // 记录 init 方法被调用的次数，使用 AtomicInteger 保证线程安全。
    val callCount = AtomicInteger(0)

    // 记录 init 方法被调用的精确时间戳 (毫秒)，使用 @Volatile 保证多线程可见性。
    @Volatile
    var executedAt: Long = 0L

    /**
     * 重写的 init 方法，增加了日志记录和通用测试逻辑。
     */
    override suspend fun init(context: Context, provider: DependenciesProvider): T {
        // 打印详细的日志，包括任务名和当前执行线程，便于调试。
        println("🚀 -> 初始值设定项 '$name' [${mode}] 在线程 [${Thread.currentThread().name}] 上启动")
        executedAt = System.currentTimeMillis()
        callCount.incrementAndGet()
        // 执行注入的 action，如果没有则返回 Unit
        @Suppress("UNCHECKED_CAST")
        val result = action?.invoke(context, provider) ?: Unit as T
        println("✅ -> 初始化器“$name”完成。")
        return result
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> = dependencies

    override fun initMode(): InitMode = mode

    override fun toString(): String {
        return "TestInitializer(name='$name', mode=$mode)"
    }
}

// =====================================================================================
// === 具体实现类，用于满足 StartupTest.kt 中的核心测试场景 =============================
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

// --- 场景 7: 循环依赖 (CycleA -> CycleB -> CycleC -> CycleA) ---
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
/**
 * 一个在初始化时必定会抛出 [RuntimeException] 的并行任务。
 */
object FailingParallelA :
    BaseTestInitializer<String>("FailingParallelA", mode = InitMode.PARALLEL, action = { _, _ ->
        // 在 action 中抛出异常，用于测试框架的异常捕获和报告能力。
        println("💥 -> 初始值设定项 'FailingParallelA' 即将抛出异常！")
        throw RuntimeException("FailingParallelA failed!")
    })

/**
 * 一个依赖于 [FailingParallelA] 的并行任务，用于测试异常传播。
 * 当其依赖项失败时，此任务应被取消，不应执行。
 */
object DependentOnFailure : BaseTestInitializer<Unit>(
    "DependentOnFailure",
    dependencies = listOf(FailingParallelA::class),
    mode = InitMode.PARALLEL
)

/**
 * 一个正常的并行任务，用于验证在有其他任务失败时，它是否能独立完成而不受影响。
 */
object NormalParallelB : BaseTestInitializer<Unit>("NormalParallelB", mode = InitMode.PARALLEL)


/**
 * 一个在 `init` 方法内部使用 `withContext` 切换线程的特殊任务。
 * 用于验证框架是否能正确处理在 `Initializer` 内部的线程切换。
 */
class ThreadSwitchingInitializer(
    // 允许从外部测试代码注入一个测试调度器，而不是硬编码 Dispatchers.IO。
    val newDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BaseTestInitializer<String>(
    name = "ThreadSwitcher",
    mode = InitMode.PARALLEL,
) {
    override suspend fun init(context: Context, provider: DependenciesProvider): String {
        // 调用父类方法以触发标准日志和统计。
        super.init(context, provider)

        val initialThreadName = Thread.currentThread().name

        // 使用 withContext 切换到指定的调度器（在测试中为 ioTestDispatcher）。
        val result = withContext(newDispatcher) {
            val ioThreadName = Thread.currentThread().name
            //虽然切换了线程,但是在测试的虚拟环境中,他们实际上还是在一个线程中执行.
            //只要切换后,业务逻辑一切正常,测试便是可行的
            println("🔄 -> 初始值设定项“ThreadSwitcher”切换到线程 [${ioThreadName}]")
            // 返回包含两个线程名的字符串，用于在测试中进行断言。
            "InitialThread: $initialThreadName, IOThread: $ioThreadName"
        }
        println("✅ -> 从 withContext 返回的初始值设定项“ThreadSwitcher”。")
        return result
    }
}

/**
 * 一个能返回特定字符串结果的任务，用于测试依赖结果的传递。
 * @param result 默认返回的字符串。
 * @param action 允许重写其行为，例如从 provider 获取其他任务的结果。
 */
class ResultInitializer(
    private val result: String,
    action: (suspend (Context, DependenciesProvider) -> String)? = { _, _ -> result },
) :
    BaseTestInitializer<String>(
        name = "ResultInitializer",
        mode = InitMode.PARALLEL,
        action = action
    )

/**
 * 一个可被外部控制的、用于测试取消逻辑的任务。
 * 它会在 `init` 方法中无限期挂起，直到被取消或手动完成。
 */
class CancellableInitializer : BaseTestInitializer<Unit>(
    name = "Cancellable",
    mode = InitMode.SERIAL // 使用串行以确保其按预期顺序启动和挂起
) {
    // 使用 CompletableDeferred 来挂起协程。
    private val completable = CompletableDeferred<Unit>()

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        super.init(context, provider)
        println("⏳ -> 初始化器“Cancellable”现在无限期暂停......")
        // 在这里挂起，等待外部调用 cancel() 或 complete()
        completable.await()
    }

    /**
     * 如果测试需要，可以从外部调用此方法来正常完成任务。
     */
    @Suppress("unused")
    fun complete() {
        println("🟢 -> 初始值设定项“Cancellable”已在外部完成。")
        completable.complete(Unit)
    }
}
