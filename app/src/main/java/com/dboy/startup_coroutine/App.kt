package com.dboy.startup_coroutine

import android.app.Application
import android.util.Log

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("AppStartup", "============== 启动流程开始 ==============")

        // 记录启动开始的时间
        val startTime = System.currentTimeMillis()

        //构建并启动 Startup 框架
        val startup = Startup(
            context = this,
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
                val duration = System.currentTimeMillis() - startTime
                Log.d(
                    "AppStartup",
                    "============== 启动流程成功结束，总耗时: $duration ms =============="
                )
            },
            // 定义任何任务失败时的回调
            onError = { errors ->
                val duration = System.currentTimeMillis() - startTime
                Log.e(
                    "AppStartup",
                    "============== 启动流程发生错误，总耗时: $duration ms =============="
                )
                // 打印所有捕获到的异常信息
                errors.forEach { error ->
                    Log.e("AppStartup", "捕获到的异常: ${error.initializerClass}", error.exception)
                }
            }
        )

        //调用 start() 方法，开始执行所有初始化任务
        startup.start()

        Log.d("AppStartup", "startup.start() 已调用，主线程继续执行其他任务...")
    }
}

