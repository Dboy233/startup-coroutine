package com.dboy.startup.coroutine.model

import kotlin.reflect.KClass

/**
 * 一个数据类，用于保存有关失败初始值设定项的信息。
 *
 * @param initializerClass 失败的初始值设定项的 KClass。
 * @param exception 导致失败的异常。
 */
data class StartupException(
    val initializerClass: KClass<*>,
    val exception: Throwable
)