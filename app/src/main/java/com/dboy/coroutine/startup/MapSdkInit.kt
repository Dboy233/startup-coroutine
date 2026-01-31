package com.dboy.coroutine.startup

import android.app.Application
import android.util.Log
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer
import kotlin.reflect.KClass

class MapSdkInit : Initializer<Unit> {
    override suspend fun init(application: Application, provider: DependenciesProvider) {
        // 模拟加载 SO 库和鉴权
        Thread.sleep(600) // 模拟 CPU 密集型操作 (如果是 IO 线程)
        Log.d("Startup", "Map SDK initialized")
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> {
        return listOf(AppConfigInit::class)
    }

    override fun isMultiProcess(): Boolean = true
}
        