package com.dboy.coroutine.startup

import android.app.Application
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer

class FistInitializer: Initializer<Unit> {
    override suspend fun init(
        application: Application,
        provider: DependenciesProvider
    ) {

    }
}