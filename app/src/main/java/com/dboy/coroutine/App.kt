package com.dboy.coroutine

import android.app.Application
import android.util.Log
import androidx.startup.AppInitializer
import com.dboy.startup.coroutine.Startup
import com.dboy.startup.coroutine.model.StartupResult
import kotlin.collections.forEach
import kotlin.system.measureTimeMillis

/**
 * 让Application更加简洁一些.
 * 其实可以让很多任务都进行并行处理,当他们并行时依赖了前置任务,他们就会自动更改为串行执行,
 * 但是在编写[com.dboy.startup.coroutine.api.Initializer]的时候,还是需要主动返回初始化模式为并行.因为串行任务会优先执行,
 * 并且并行任务无法依赖串行任务.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        jetpackInitStartup()
        initStartupCoroutine()
    }

    private fun jetpackInitStartup() {
        Log.d("StartupJetpack", "============== StartupJetpack 启动流程开始 ==============")

        val time = measureTimeMillis {
            //需要自己弄明白依赖关系,只需要初始化两个末端任务即可,其内部会自动初始化他们依赖的任务
            AppInitializer.getInstance(this).apply {
                initializeComponent(JectpacjBugMonitorInitializer::class.java)
                initializeComponent(JectpackAdsPlatformInitializer::class.java)
            }
        }

        Log.i("StartupJetpack", "==============StartupJetpack 用时统计==============")
        timeStatistics.forEach {
            Log.i("StartupJetpack", it)
        }

        Log.i("StartupJetpack", "StartupJetpack 总共耗时: $time")

        Log.i(
            "StartupJetpack",
            "============== StartupJetpack 启动流程成功结束==============\n.\n."
        )
    }

    private fun initStartupCoroutine() {
        Log.d("StartupCoroutine", "============== 启动流程开始 ==============")

        //构建并启动 Startup 框架
        val startup = Startup(
            context = this,
            isDebug = true,
            // 定义所有需要执行的初始化任务列表,无需排序
            initializers = listOf(
                BugMonitorInitializer(),
                CommonUtilsInitializer(),
                ConfigInitializer(),
                DatabaseInitializer(),
                AdsPlatformInitializer(),
            ),
            onResult = {
                when (it) {
                    is StartupResult.Failure -> {
                        Log.e(
                            "StartupCoroutine",
                            "============== 启动流程发生错误 =============="
                        )
                        it.exceptions.forEach { error ->
                            Log.e(
                                "StartupCoroutine",
                                "任务${error.initializerClass}执行失败",
                                error.exception
                            )
                        }
                    }

                    StartupResult.Success -> {
                        Log.d(
                            "StartupCoroutine",
                            "============== 启动流程成功结束=============="
                        )
                    }
                }
            }
        )

        //调用 start() 方法，开始执行所有初始化任务
        startup.start()

        Log.d("StartupCoroutine", "startup.start() 已调用，主线程继续执行其他任务...")
    }
}

