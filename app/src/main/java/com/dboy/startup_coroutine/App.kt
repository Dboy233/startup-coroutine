package com.dboy.startup_coroutine

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Startup(
            this,
            listOf(InitD(), InitC(), InitB(), InitA()),
            onCompletion = { },
            onError = {}
        ).start()
    }
}