package com.dboy.coroutine.startup

import android.app.Application
import android.util.Log
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

class AdMobInit : Initializer<Unit> {
    override suspend fun init(application: Application, provider: DependenciesProvider) {
        // 许多广告SDK强制要求在主线程初始化
        withContext(Dispatchers.Main) {
            // 模拟 SDK 内部的繁重初始化逻辑（不阻塞主线程，但占用时间）
            delay(1500)
            Log.d("Startup", "AdMob initialized")
        }
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> {
        return listOf(AppConfigInit::class)
    }
}
        