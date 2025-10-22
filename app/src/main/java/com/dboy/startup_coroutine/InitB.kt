package com.dboy.startup_coroutine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

/**
 * 串行B
 */
class InitB : Initializer<Unit>() {
    override suspend fun init(
        context: Context,
        dependenciesProvider: DependenciesProvider
    ) {
        Log.d("Initializer", "initB:串行任务 开始执行")
        //获取用户配置文件，没有返回结果
        delay(500)
        Log.d("Initializer", "initB:串行任务 执行结束")

    }
    override fun initMode(): InitMode {
        return InitMode.SERIAL
    }
}