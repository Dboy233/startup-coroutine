@file:Suppress("SpellCheckingInspection")

package com.dboy.coroutine

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

/**
 * =====================================================================================
 *
 *              使用 Jetpack Startup
 *
 * =====================================================================================
 *
 * **任务依赖图:**
 *
 *  JectpacjBugMonitorInitializer (无依赖, 独立执行)
 *
 *  JetcpackCommonUtilsInitializer (无依赖, 独立执行)
 *        |
 *        +-----> [BG] JetcPackDatabaseInitializer
 *        |           |
 *        +-----> [Main] JetpackConfigInitializer
 *                    |
 *                    +-----> [Main] JectpackAdsPlatformInitializer
 */

// 模拟的配置数据类
data class JetPackAppConfig(val adConfig: Map<String, String>, val featureFlags: Set<String>)

val timeStatistics = mutableListOf<String>()

/**
 * 用于在不同 Initializer 之间安全地传递结果的静态存储对象。
 */
object JetpackStartupResults {
    private val results = mutableMapOf<Class<out Initializer<*>>, Any?>()

    fun <T> put(key: Class<out Initializer<*>>, value: T) {
        results[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: Class<out Initializer<*>>): T? {
        return results[key] as? T
    }
}


// --- 任务1: Bug统计平台初始化 (高优先级, 无依赖) ---
class JetpackBugMonitorInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val time = measureTimeMillis {
            Log.d(
                "StartupJetpack",
                "1. [BugMonitor] (${Thread.currentThread().name}) 开始初始化Bug统计平台..."
            )
            Thread.sleep(100)
            Log.d(
                "StartupJetpack",
                "1. [BugMonitor] (${Thread.currentThread().name}) ✅ Bug统计平台初始化完成。"
            )
        }
        timeStatistics.add("- JectpacjBugMonitorInitializer   | $time")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}


// --- 任务2: 通用工具类初始化 (无依赖) ---

class JetpackCommonUtilsInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val time = measureTimeMillis {
            Log.d(
                "StartupJetpack",
                "2. [Utils] (${Thread.currentThread().name}) 开始初始化通用工具库..."
            )
            Log.d(
                "StartupJetpack",
                "2.1 [Utils] (${Thread.currentThread().name}) ...日志、网络、统计、EventBus等工具OK"
            )
            Thread.sleep(500)
            Log.d(
                "StartupJetpack",
                "2. [Utils] (${Thread.currentThread().name}) ✅ 通用工具库全部初始化完成。"
            )
        }
        timeStatistics.add("- JetcpackCommonUtilsInitializer    | $time")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}


// --- 任务3: 数据库初始化 (依赖工具库) ---

class JetPackDatabaseInitializer : Initializer<Boolean> {
    override fun create(context: Context): Boolean {
        val time = measureTimeMillis {
            Log.d(
                "StartupJetpack",
                "3. [Database] (${Thread.currentThread().name}) 开始初始化数据库..."
            )
            Log.d(
                "StartupJetpack",
                "3. [Database] (${Thread.currentThread().name}) ...检测到数据库需要升级，执行升级操作..."
            )
            Thread.sleep(300)
            Log.d(
                "StartupJetpack",
                "3. [Database] (${Thread.currentThread().name}) ✅ 数据库初始化完成。"
            )
        }
        timeStatistics.add("- JetcPackDatabaseInitializer | $time")
        return true
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(JetpackCommonUtilsInitializer::class.java)
}


// --- 任务4: 配置信息初始化 (依赖工具库和数据库) ---

class JetpackConfigInitializer : Initializer<JetPackAppConfig> {
    override fun create(context: Context): JetPackAppConfig {
        var config: JetPackAppConfig
        val time = measureTimeMillis {
            Log.d(
                "StartupJetpack",
                "4. [Config] (${Thread.currentThread().name}) 开始从网络获取配置信息..."
            )

            config = runBlocking(Dispatchers.IO) {
                // 此处在IO线程中执行
                delay(50L)

                // 模拟获取到的配置
                JetPackAppConfig(
                    adConfig = mapOf("provider" to "AwesomeAds", "timeout" to "3000"),
                    featureFlags = setOf("new_checkout_flow", "enable_dark_mode")
                )
            }

            Log.d(
                "StartupJetpack",
                "4. [Config] (${Thread.currentThread().name}) ✅ 配置信息获取成功。"
            )
            // 将结果存入共享对象，供下游任务使用
            JetpackStartupResults.put(JetpackConfigInitializer::class.java, config)
        }
        timeStatistics.add("- JetpackConfigInitializer    | $time ms")
        return config
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(
        JetpackCommonUtilsInitializer::class.java, JetPackDatabaseInitializer::class.java
    )
}


// --- 任务5: 广告平台初始化 (依赖配置信息) ---

class JetpackAdsPlatformInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val time = measureTimeMillis {// 这个任务是依赖链的末端，通常在主线程执行SDK的初始化方法
            Log.d(
                "StartupJetpack",
                "5. [Ads] (${Thread.currentThread().name}) 开始初始化广告平台..."
            )

            // 从共享结果中获取上游任务传递的配置信息
            val config =
                JetpackStartupResults.get<JetPackAppConfig>(JetpackConfigInitializer::class.java)

            if (config != null) {
                Log.d(
                    "StartupJetpack",
                    "5. [Ads] (${Thread.currentThread().name}) ...使用配置: ${config.adConfig}"
                )
                // 模拟基于配置的初始化
                Thread.sleep(200)
                Log.d(
                    "StartupJetpack",
                    "5. [Ads] (${Thread.currentThread().name}) ✅ 广告平台初始化完成。"
                )
            } else {
                Log.e(
                    "StartupJetpack",
                    "5. [Ads] (${Thread.currentThread().name}) ❌ 初始化失败，未能获取到配置信息！"
                )
            }
        }
        timeStatistics.add("- JectpackAdsPlatformInitializer    | ${time}ms")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(JetpackConfigInitializer::class.java)
}
