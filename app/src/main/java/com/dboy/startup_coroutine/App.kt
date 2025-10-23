package com.dboy.startup_coroutine

import android.app.Application
import android.util.Log

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        Log.d("AppStartup", "============== 启动流程开始 ==============")
        val startTime = System.currentTimeMillis()

        val startup = Startup(
            context = this,
            initializers = listOf(
                // 添加所有需要执行的任务
                ConfigInitializer(),
                LogInitializer(),
                UserAuthInitializer(),
                DatabaseInitializer(),
                AdsInitializer(),
                UIThemeInitializer()
            ),
            onCompletion = {
                val duration = System.currentTimeMillis() - startTime
                Log.d("AppStartup", "============== ✅ 启动流程成功完成 (耗时: ${duration}ms) ==============")
                // 在这里可以认为App已准备好，可以展示主界面
            },
            onError = { errors ->
                val duration = System.currentTimeMillis() - startTime
                Log.e("AppStartup", "============== 🔥 启动流程失败 (耗时: ${duration}ms) ==============")
                errors.forEach { error ->
                    Log.e("AppStartup", "错误详情: ", error)
                }
                // 在这里可以进行错误上报或降级处理
            }
        )

        startup.start()
    }
}