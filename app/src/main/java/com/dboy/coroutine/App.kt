package com.dboy.coroutine

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import com.dboy.coroutine.startup.AdMobInit
import com.dboy.coroutine.startup.AppConfigInit
import com.dboy.coroutine.startup.ExceptionInit
import com.dboy.coroutine.startup.FistInitializer
import com.dboy.coroutine.startup.FlutterEngineInit
import com.dboy.coroutine.startup.IMInit
import com.dboy.coroutine.startup.MapSdkInit
import com.dboy.coroutine.utils.SpUtils
import com.dboy.startup.coroutine.Startup
import com.dboy.startup.coroutine.StartupDispatchers

/**
 * 让Application更加简洁一些.
 */
class App : Application() {

    companion object {

        lateinit var startup: Startup

        fun startInit() {
            startup.start()
        }

        fun cancelInit() {
            startup.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 1. 获取当前进程名并进行过滤判断
        //    这是多进程适配的关键步骤，确保只有指定进程才执行应用级别的初始化。
        val currentProcessName = getCurrentProcessName() ?: ""
        val isMainProcess = currentProcessName == packageName

        // 如果既不是主进程，也不是我们明确允许运行 startup-coroutine 的子进程，则直接返回。
        // 这将避免在第三方SDK等不相关的进程中运行我们自身的初始化逻辑，节约资源并避免冲突。
        if (!isMainProcess && !isOurWhitelistedProcess(currentProcessName)) {
            return
        }

        // 2. 初始化跨进程安全的公共组件（例如 MMKV 替代方案）
        //    SpUtils 仅作为示例，在实际多进程项目中，建议替换为 MMKV 等真正支持多进程的存储方案。
        SpUtils.init(this)

        // 3. 构建 Startup 初始化任务列表
        val startupInitializers = listOf(
            AdMobInit(),
            AppConfigInit(),
            FlutterEngineInit(),
            FistInitializer(),
            IMInit(),
            MapSdkInit(),
            ExceptionInit()
        )
        
        // 4. 配置并构建 Startup 框架实例
        startup = Startup.Builder(this)
            .setDispatchers(StartupDispatchers.Default) // 使用默认的协程调度器配置
            .setDebug(true) // 开启调试模式，会输出详细日志
            .add(startupInitializers) // 添加所有定义好的初始化任务
            .build()
        
        // 5. 根据条件触发 Startup 初始化流程
        //    这里演示了根据用户是否同意隐私协议来决定是否立即启动初始化。
        //    在某些App生命周期管理复杂的场景（如进程重启），可能需要根据持久化状态来决定是否再次启动。
        //    这一步很关键！！
        if (SpUtils.getBoolean("isAgreePrivacy", false)) {
            startInit() // 启动所有注册的初始化任务
        }
    }

    /**
     * 如果我们有自己的子进程任务，必须要判断进程是否是自己的。
     * 如果不是自己的进程，不允许进行初始化。
     * 第三方SDK获取会频繁启动子进程进行数据上报或者其他处理，所以第三方子进程在没有明确要求的情况下不让他们进行初始化。
     */
    private fun isOurWhitelistedProcess(processName: String): Boolean {
        // 只有我们明确需要运行 startup-coroutine 框架的子进程才应在此列出。
        // 例如，:webview 进程需要初始化部分SDK。
        return processName.contains(":webview")
    }

    private fun getCurrentProcessName(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.find { it.pid == Process.myPid() }?.processName
        }
    }

}

