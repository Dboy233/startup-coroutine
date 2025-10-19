package com.dboy.startup_coroutine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

/**
 * 并行D
 */
class InitD: Initializer<Unit>() {
    override suspend fun init(
        context: Context,
        resultDispatcher: ResultDispatcher
    ) {
        Log.d("Initializer", "initD:并行任务 开始执行")
        delay(200)
        Log.d("Initializer", "initD:并行任务 执行结束")
    }
}