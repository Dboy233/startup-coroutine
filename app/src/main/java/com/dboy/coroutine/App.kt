package com.dboy.coroutine

import android.app.Application
import android.util.Log
import com.dboy.startup.coroutine.ExecuteOnIODispatchers
import com.dboy.startup.coroutine.Startup

/**
 * 让Application更加简洁一些.
 * 其实可以让很多任务都进行并行处理,当他们并行时依赖了前置任务,他们就会自动更改为串行执行,
 * 但是在编写[com.dboy.startup.coroutine.api.Initializer]的时候,还是需要主动返回初始化模式为并行.因为串行任务会优先执行,
 * 并且并行任务无法依赖串行任务.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("AppStartup", "============== 启动流程开始 ==============")

        //构建并启动 Startup 框架
        val startup = Startup(
            context = this,
            isDebug = true,
            dispatchers = ExecuteOnIODispatchers,
            // 定义所有需要执行的初始化任务列表
            initializers = listOf(
                PrivacyConsentInitializer(),
                NetworkInitializer(),
                LoggingInitializer(),
                ConfigInitializer(),
                UserAuthInitializer(),
                DatabaseInitializer(),
                UIThemeInitializer(),
                ThirdPartySDKInitializer(),
                UnnecessaryAnalyticsInitializer(),
            ),
            // 定义所有任务成功完成后的回调
            onCompletion = {
                Log.d(
                    "AppStartup",
                    "============== 启动流程成功结束=============="
                )
            },
            // 定义任何任务失败时的回调
            onError = { errors ->
                Log.e(
                    "AppStartup",
                    "============== 启动流程发生错误 =============="
                )
                errors.forEach {
                    Log.e("AppStartup", "任务${it.initializerClass}执行失败")
                }
            }
        )

        //调用 start() 方法，开始执行所有初始化任务
        startup.start()

        Log.d("AppStartup", "startup.start() 已调用，主线程继续执行其他任务...")
    }
}

