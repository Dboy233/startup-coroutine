package com.dboy.startup.coroutine.api

import kotlin.reflect.KClass

interface StartupProvider {
    fun getInitializers(): List<KClass<out Initializer<*>>>
}