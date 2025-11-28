package com.dboy.coroutine.startup

import android.app.Application
import android.util.Log
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer
import kotlinx.coroutines.delay

class FlutterEngineInit : Initializer<Unit> {
    override suspend fun init(application: Application, provider: DependenciesProvider) {
        // 模拟引擎预热
        delay(2500)
        Log.d("Startup", "Flutter Engine warmed up")
    }
}
        