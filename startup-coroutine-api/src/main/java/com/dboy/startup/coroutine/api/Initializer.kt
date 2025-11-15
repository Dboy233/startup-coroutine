package com.dboy.startup.coroutine.api

interface Initializer<T> {

    suspend fun init(provider: DependenciesProvider): T
}