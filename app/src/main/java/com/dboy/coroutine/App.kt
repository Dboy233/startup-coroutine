package com.dboy.coroutine

import android.app.Application

/**
 * 让Application更加简洁一些.
 * 其实可以让很多任务都进行并行处理,当他们并行时依赖了前置任务,他们就会自动更改为串行执行,
 * 但是在编写[com.dboy.startup.coroutine.api.Initializer]的时候,还是需要主动返回初始化模式为并行.因为串行任务会优先执行,
 * 并且并行任务无法依赖串行任务.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

    }

}

