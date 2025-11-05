package com.dboy.startup.coroutine.inter

import com.dboy.startup.coroutine.api.Initializer

interface IDependencyGraph {

    fun sortAndValidate(): List<Initializer<*>>

}