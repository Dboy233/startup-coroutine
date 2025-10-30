package com.dboy.coroutine

import android.content.Context
import android.util.Log
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/**
 * =====================================================================================
 *
 *              使用 startup-coroutine 框架实现的启动流程
 *
 * =====================================================================================
 *
 * **任务依赖图:**
 *
 *   BugMonitorInitializer (无依赖, 独立执行)
 *
 *   CommonUtilsInitializer (无依赖, 独立执行)
 *        |
 *        +-----> [P] DatabaseInitializer
 *        |           |
 *        +-----> [P] ConfigInitializer
 *                    |
 *                    +-----> [P] AdsPlatformInitializer
 */

// --- 模拟数据类 ---

// APP配置信息
data class AppConfig(val adConfig: Map<String, String>, val featureFlags: Set<String>)


// --- 初始化任务实现 ---

/**
 * **任务1 : 初始化Bug统计平台**
 *
 * 这是一个高优先级的独立任务，不依赖任何其他任务，可以在启动后立即在后台执行。
 */
class BugMonitorInitializer : Initializer<Unit>() {

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        Log.d(
            "StartupCoroutine",
            "1. [BugMonitor] (${Thread.currentThread().name}) 开始初始化Bug统计平台..."
        )
        delay(100)
        Log.d(
            "StartupCoroutine",
            "1. [BugMonitor] (${Thread.currentThread().name}) ✅ Bug统计平台初始化完成。"
        )
    }
}

/**
 * **任务2 : 初始化通用工具类**
 *
 * 包含多个子模块，总耗时较长，适合在后台并行执行。它也不依赖任何其他任务。
 */
class CommonUtilsInitializer : Initializer<Unit>() {

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        Log.d(
            "StartupCoroutine",
            "2. [Utils] (${Thread.currentThread().name}) 开始初始化通用工具库..."
        )
        delay(500) // 模拟多个工具库的综合初始化耗时
        Log.d(
            "StartupCoroutine",
            "2.1 [Utils] (${Thread.currentThread().name}) ...日志、网络、统计、EventBus等工具OK"
        )
        Log.d(
            "StartupCoroutine",
            "2. [Utils] (${Thread.currentThread().name}) ✅ 通用工具库全部初始化完成。"
        )
    }
}

/**
 * **任务3 : 初始化数据库**
 *
 * 依赖通用工具库（用于日志、统计等），它本身是一个耗时的IO操作，应在后台执行。
 */
class DatabaseInitializer : Initializer<Unit>() {

    // 声明依赖 CommonUtilsInitializer
    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(CommonUtilsInitializer::class)

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        withContext(Dispatchers.IO) {
            Log.d(
                "StartupCoroutine",
                "3. [Database] (${Thread.currentThread().name}) 开始初始化数据库..."
            )
            // 模拟数据库升级检查
            Log.d(
                "StartupCoroutine",
                "3. [Database] (${Thread.currentThread().name}) ...检测到数据库需要升级，执行升级操作..."
            )
            delay(300)
            Log.d(
                "StartupCoroutine",
                "3. [Database] (${Thread.currentThread().name}) ✅ 数据库初始化完成。"
            )
        }
    }
}

/**
 * **任务4 : 初始化配置信息**
 *
 * 依赖工具库和数据库，并通过网络获取数据。这是典型的异步依赖场景。
 */
class ConfigInitializer : Initializer<AppConfig>() {

    // 声明依赖 CommonUtilsInitializer 和 DatabaseInitializer
    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(
        CommonUtilsInitializer::class,
        DatabaseInitializer::class
    )

    override suspend fun init(context: Context, provider: DependenciesProvider): AppConfig {
        // 框架会自动等待依赖项完成，无需手动处理
        return withContext(Dispatchers.IO) {
            Log.d(
                "StartupCoroutine",
                "4. [Config] (${Thread.currentThread().name}) 开始从网络获取配置信息..."
            )
            delay(50L)
            // 模拟获取到的配置
            val config = AppConfig(
                adConfig = mapOf("provider" to "AwesomeAds", "timeout" to "3000"),
                featureFlags = setOf("new_checkout_flow", "enable_dark_mode")
            )
            Log.d(
                "StartupCoroutine",
                "4. [Config] (${Thread.currentThread().name}) ✅ 配置信息获取成功: $config"
            )
            config
        }
    }
}

/**
 * **任务5 : 初始化广告平台**
 *
 * 依赖配置信息任务，获取到配置后才能进行初始化。
 */
class AdsPlatformInitializer : Initializer<Unit>() {

    // 声明依赖 ConfigInitializer
    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(ConfigInitializer::class)

    override suspend fun init(context: Context, provider: DependenciesProvider) {
        // 直接、安全地获取上游任务的结果。如果ConfigInitializer失败，这里会抛出异常，由框架统一捕获。
        val config = provider.result<AppConfig>(ConfigInitializer::class)

        Log.d("StartupCoroutine", "5. [Ads] (${Thread.currentThread().name}) 开始初始化广告平台...")
        Log.d(
            "StartupCoroutine",
            "5. [Ads] (${Thread.currentThread().name}) ...使用配置: ${config.adConfig}"
        )
        delay(200) // 模拟SDK的初始化耗时
        Log.d("StartupCoroutine", "5. [Ads] (${Thread.currentThread().name}) ✅ 广告平台初始化完成。")
    }
}
