package com.dboy.startup.coroutine.imp

import android.util.Log
import com.dboy.startup.coroutine.api.IPrinter
import com.dboy.startup.coroutine.api.Initializer
import com.dboy.startup.coroutine.model.TaskMetrics

internal class DefaultPrinter() : IPrinter {


    override fun printTopologySortingDependGraph(sortedInitializers: List<Initializer<*>>) {
        val logContent = StringBuilder("\n--- Startup Coroutine Dependency Graph ---\n\n")

        sortedInitializers.forEach { initializer ->
            logContent.append("${initializer::class.simpleName}\n")

            val dependencies = initializer.dependencies()
            if (dependencies.isNotEmpty()) {
                dependencies.forEachIndexed { index, depClass ->
                    val prefix = if (index == dependencies.size - 1) "  └─ " else "  ├─ "
                    logContent.append("$prefix${depClass.simpleName}\n")
                }
            }
        }
        logContent.append("\n----------------------------------------")
        // 使用 Log.d 打印，以便在 Android Logcat 中查看
        Log.i("StartupCoroutine", logContent.toString())
        println(logContent.toString())
    }

    override fun printRunningTimeConsumingSummaries(
        totalDuration: Long,
        taskMetrics: List<TaskMetrics>,
        dispatchersMode: String,
        hasErrors: Boolean
    ) {
        val logContent = StringBuilder("\n--- Startup Coroutine Performance Summary ---\n\n")

        // 1. 打印总览
        val status = if (hasErrors) "FAILED" else "SUCCESS"
        logContent.append(">> Total Time: ${totalDuration}ms  |  Status: $status\n")
        val dispatchersMode = dispatchersMode
        logContent.append(">> Dispatchers Mode: $dispatchersMode\n\n")
        logContent.append(">> Individual Task Durations:\n")

        // 2. 打印每个任务的耗时，并按耗时降序排列
        if (taskMetrics.isNotEmpty()) {
            // 对任务按耗时从高到低排序
            val sortedMetrics = taskMetrics.sortedByDescending { it.duration }

            val maxNameLength = sortedMetrics.maxOfOrNull { it.name.length } ?: 20
            val maxDurationLength = sortedMetrics.maxOfOrNull { it.duration.toString().length } ?: 5
            val cumulativeTime = sortedMetrics.sumOf { it.duration }

            sortedMetrics.forEach { metric ->
                val namePadded = metric.name.padEnd(maxNameLength, ' ')
                val durationPadded = "${metric.duration}ms".padEnd(maxDurationLength + 2, ' ')
                logContent.append("   - $namePadded  |  $durationPadded  |  Thread: ${metric.threadName}\n")
            }
            logContent.append(">> Task time is sum  : $cumulativeTime ms\n")
        } else {
            logContent.append("   No tasks were executed.\n")
        }

        logContent.append("\n-------------------------------------------\n")

        if (hasErrors) {
            Log.i("StartupCoroutine", logContent.toString())
            println(logContent.toString())
        } else {
            Log.i("StartupCoroutine", logContent.toString())
            println(logContent.toString())
        }
    }
}