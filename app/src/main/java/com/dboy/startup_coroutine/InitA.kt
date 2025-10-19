package com.dboy.startup_coroutine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

/**
 * 并行A
 */
class InitA : Initializer<String>() {
    override suspend fun init(
        context: Context,
        resultDispatcher: ResultDispatcher
    ): String {
        Log.d("Initializer", "init: A 网络请求用户数据")
        delay(1000)
        Log.d("Initializer", "InitA 初始化完成")
        return """{"user":"小明"}"""
    }


}