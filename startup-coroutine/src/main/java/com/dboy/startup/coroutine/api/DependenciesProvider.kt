package com.dboy.startup.coroutine.api

import kotlin.reflect.KClass

interface DependenciesProvider {

    fun <T> result(dependency: KClass<out Initializer<*>>): T

    fun <T> resultOrNull(dependency: KClass<out Initializer<*>>): T?
}
