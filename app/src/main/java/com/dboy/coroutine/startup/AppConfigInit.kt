package com.dboy.coroutine.startup

import android.app.Application
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer
import kotlinx.coroutines.delay
import kotlin.reflect.KClass

class AppConfigInit: Initializer<String> {
    override suspend fun init(
        application: Application,
        provider: DependenciesProvider
    ) : String{
        delay(100)
        return "appId = 10"
    }

    override fun dependencies(): List<KClass<out Initializer<*>>> {
        return listOf(FistInitializer::class)
    }
}