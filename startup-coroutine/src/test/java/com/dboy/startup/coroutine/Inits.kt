package com.dboy.startup.coroutine

import android.app.Application
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer
import kotlinx.coroutines.delay
import kotlin.reflect.KClass

// 1. 前置初始化 (基础库，无依赖)
class PreInit : Initializer<String> {
    override suspend fun init(application: Application, provider: DependenciesProvider): String {
        println("执行前置初始化...")
        delay(100) // 模拟耗时
        return "PreInitDone"
    }
}

// 2. 请求网络配置 (依赖 PreInit)
class NetworkConfigInit : Initializer<Map<String, String>> {
    override suspend fun init(
        application: Application,
        provider: DependenciesProvider
    ): Map<String, String> {
        val preResult = provider.result<String>(PreInit::class)
        println("执行网络配置请求 (依赖: $preResult)...")
        delay(500) // 模拟网络请求耗时
        return mapOf("ad_enabled" to "true", "db_version" to "2")
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(PreInit::class)
}

// 3. 数据库初始化 (依赖 PreInit)
class DatabaseInit : Initializer<String> {
    override suspend fun init(application: Application, provider: DependenciesProvider) : String{
        println("执行数据库初始化...")
        delay(300) // 模拟IO操作
//        throw RuntimeException("我tm崩溃了")
        return "DatabaseInitDone"
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(PreInit::class)
}

// 4. 广告初始化 (依赖 NetworkConfigInit，必须等配置回来)
class AdsInit : Initializer<Boolean> {
    override suspend fun init(application: Application, provider: DependenciesProvider): Boolean {
        val config = provider.result<Map<String, String>>(NetworkConfigInit::class)
        val isAdEnabled = config["ad_enabled"] == "true"

        println("执行广告初始化 (依赖配置: ad_enabled=$isAdEnabled)...")
        delay(200)

        if (!isAdEnabled) {
            println("广告开关关闭，跳过初始化")
            return false
        }
        return true
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(NetworkConfigInit::class)
}

// 5. 其他初始化 (依赖 DatabaseInit)
class OtherInit : Initializer<Unit> {
    override suspend fun init(application: Application, provider: DependenciesProvider) {
        // 只是为了确保数据库已经准备好了
        provider.result<String>(DatabaseInit::class)
        println("执行其他业务初始化...")
        delay(100)
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> = listOf(DatabaseInit::class)
}

// 6. 循环依赖测试 A (依赖 B)
class CircularDependencyInitA : Initializer<Unit> {
    override suspend fun init(application: Application, provider: DependenciesProvider) {
        println("初始化 CircularDependencyInitA...")
        provider.result<Unit>(CircularDependencyInitB::class)
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(CircularDependencyInitB::class)
}

// 7. 循环依赖测试 B (依赖 A) - 与 A 构成死锁环
class CircularDependencyInitB : Initializer<Unit> {
    override suspend fun init(application: Application, provider: DependenciesProvider) {
        println("初始化 CircularDependencyInitB...")
        provider.result<Unit>(CircularDependencyInitA::class)
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> =
        listOf(CircularDependencyInitA::class)
}

// 8. 边缘任务初始化失败 (不应该影响主流程)
class MarginalTaskInit : Initializer<Unit> {
    override suspend fun init(application: Application, provider: DependenciesProvider) {
        println("执行边缘任务初始化...")
        delay(100)
        throw RuntimeException("边缘任务初始化失败了，但不应崩溃应用")
    }

    // 无依赖，或者依赖一些基础库
    override fun dependencies(): List<KClass<out Initializer<*>>> = emptyList()
}




