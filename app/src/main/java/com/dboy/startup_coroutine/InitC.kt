package com.dboy.startup_coroutine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

/**
 * 串行C
 */
class InitC : Initializer<Unit>() {
    override suspend fun init(
        context: Context,
        dependenciesProvider: DependenciesProvider
    ) {
        Log.d("Initializer", "initC:并行任务 开始执行")
        val result = dependenciesProvider.getResult(InitA::class.java)
        delay(500)
        Log.d("Initializer", "initC:并行任务 执行结束:得到A数据${result}")

    }


    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(InitA::class.java)
    }

    override fun initMode(): InitMode {
        return InitMode.SERIAL
    }
}