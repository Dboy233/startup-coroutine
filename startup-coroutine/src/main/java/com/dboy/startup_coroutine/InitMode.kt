package com.dboy.startup_coroutine
/**
 * 初始化模式，分为串行和并行。
 */
enum class InitMode {
    /**
     * 串行初始化：任务将会在一个单独的协程中按顺序执行。
     */
    SERIAL,

    /**
     * 并行初始化：任务将会并发执行，充分利用多核CPU。
     */
    PARALLEL
}