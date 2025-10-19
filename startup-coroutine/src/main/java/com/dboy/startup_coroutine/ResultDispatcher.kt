package com.dboy.startup_coroutine
/**
 * 调度器接口，提供在初始化任务中获取其他依赖任务结果的能力。
 */
interface ResultDispatcher {
    /**
     * 根据 Class 类型获取已完成的依赖项的初始化结果。
     *
     * @param T 期望返回的结果类型。
     * @param dependency 依赖项的 Class 对象。
     * @return 依赖项的初始化结果。如果依赖项未完成或不存在，会抛出异常。
     * @throws IllegalStateException 如果依赖项尚未初始化或不存在。
     */
    fun <T> getResult(dependency: Class<out Initializer<T>>): T
}