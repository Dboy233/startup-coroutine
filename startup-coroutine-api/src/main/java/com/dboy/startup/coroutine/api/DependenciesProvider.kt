package com.dboy.startup.coroutine.api

import kotlin.reflect.KClass

interface DependenciesProvider {
    /**
     * 根据类型获取依赖实例。
     * 如果有多个相同类型的依赖，会抛出异常。
     */
    fun <T : Any> get(type: KClass<T>): T

    /**
     * 根据类型和别名获取一个特定的依赖实例。
     */
    fun <T : Any> get(type: KClass<T>, alias: String): T

    // 提供一个 getOrNull 的版本会更健壮
    fun <T : Any> getOrNull(type: KClass<T>): T?
    fun <T : Any> getOrNull(type: KClass<T>, alias: String): T?
}