package com.dboy.coroutine.startup

import android.app.Application
import android.util.Log
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer
import kotlinx.coroutines.delay
import kotlin.reflect.KClass

class IMInit : Initializer<Unit> {
    override suspend fun init(application: Application, provider: DependenciesProvider) {
        // 模拟数据库连接和长链接建立
        delay(2000)
        Log.d("Startup", "IM SDK initialized")
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> {
        return listOf(AppConfigInit::class)
    }
}
        