package com.dboy.coroutine

import android.app.Application
import com.dboy.coroutine.startup.AdMobInit
import com.dboy.coroutine.startup.AppConfigInit
import com.dboy.coroutine.startup.ExceptionInit
import com.dboy.coroutine.startup.FistInitializer
import com.dboy.coroutine.startup.FlutterEngineInit
import com.dboy.coroutine.startup.IMInit
import com.dboy.coroutine.startup.MapSdkInit
import com.dboy.coroutine.utils.SpUtils
import com.dboy.startup.coroutine.Startup
import com.dboy.startup.coroutine.StartupDispatchers

/**
 * 让Application更加简洁一些.
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
        //轻量级数据存储推荐直接初始化,用于进行判断逻辑.
        SpUtils.init(this)

        //构建初始化任务列表.
        val initializer = listOf(
            AdMobInit(),
            AppConfigInit(),
            FlutterEngineInit(),
            FistInitializer(),
            IMInit(),
            MapSdkInit(),
            ExceptionInit()
        )
        //构建startup
        startup = Startup.Builder(this)
            .setDispatchers(StartupDispatchers.AllIO)
            .setDebug(true)
            .add(initializer)
            .build()

        //推荐!!!
        //如果App跳过了手动初始化,就必须做判断,只要条件允许第二次初始化的时候
        //一定在Application中直接执行.
        //这里涉及到了一些进程重启逻辑.
        if (SpUtils.getBoolean("isAgreePrivacy",false)) {
            startInit()
        }
    }

}

