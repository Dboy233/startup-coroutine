package com.dboy.startup.coroutine.api

import android.app.Application
import kotlin.reflect.KClass

interface Initializer<T> {

    suspend fun init(application: Application, provider: DependenciesProvider): T


    fun dependencies(): List<KClass<out Initializer<*>>> = emptyList()

}