package com.dboy.startup.coroutine.initializers

import android.content.Context
import com.dboy.startup.coroutine.api.DependenciesProvider
import com.dboy.startup.coroutine.api.Initializer

class ContextInitializer(val context: Context) : Initializer<Context> {
    override suspend fun init(provider: DependenciesProvider): Context {
        return context
    }
}
