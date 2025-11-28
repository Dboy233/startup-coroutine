package com.dboy.coroutine.startup

import android.app.Application
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer

class ExceptionInit: Initializer<Unit> {
    override suspend fun init(
        application: Application,
        provider: DependenciesProvider
    ) {
         throw RuntimeException("我注定要失败")
    }
}