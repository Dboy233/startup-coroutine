package com.dboy.coroutine

import android.app.Application
import com.dboy.coroutine.startup.AdMobInit
import com.dboy.coroutine.startup.AppConfigInit
import com.dboy.coroutine.startup.ExceptionInit
import com.dboy.coroutine.startup.FistInitializer
import com.dboy.coroutine.startup.FlutterEngineInit
import com.dboy.coroutine.startup.IMInit
import com.dboy.coroutine.startup.MapSdkInit
import com.dboy.startup.coroutine.AllIODispatchers
import com.dboy.startup.coroutine.Startup

/**
 * 让Application更加简洁一些.
 * 其实可以让很多任务都进行并行处理,当他们并行时依赖了前置任务,他们就会自动更改为串行执行,
 * 但是在编写[com.dboy.startup.coroutine.api.Initializer]的时候,还是需要主动返回初始化模式为并行.因为串行任务会优先执行,
 * 并且并行任务无法依赖串行任务.
 */
class App : Application() {

    companion object {
        private lateinit var startup: Startup

        fun startInit() {
            startup.start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        SpUtils.init(this)
        val initializer = listOf(
            AdMobInit(),
            AppConfigInit(),
            FlutterEngineInit(),
            FistInitializer(),
            IMInit(),
            MapSdkInit(),
            ExceptionInit()
        )
        startup = Startup.Builder(this)
            .setDispatchers(AllIODispatchers)
            .setDebug(true)
            .add(initializer)
            .build()
        if (SpUtils.getBoolean("isAgreePrivacy",false)) {
            startInit()
        }
    }

}

