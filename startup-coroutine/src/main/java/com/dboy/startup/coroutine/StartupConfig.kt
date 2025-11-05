package com.dboy.startup.coroutine

import com.dboy.startup.coroutine.model.StartupResult

data class StartupConfig(
    val isDebug: Boolean = false,
    val dispatchers: StartupDispatchers = DefaultDispatchers,
    val onResult: ((StartupResult) -> Unit)? = null
)
