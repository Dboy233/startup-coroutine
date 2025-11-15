package com.dboy.startup.coroutine.imp

import android.content.Context
import com.dboy.startup.coroutine.api.IStartup

class StartupImp(builder: Builder): IStartup {

    override fun start() {

    }

    override fun cancel() {

    }

    class Builder(internal val context: Context){

        fun build(): StartupImp {
            return StartupImp(this)
        }
    }
}