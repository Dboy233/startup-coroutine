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
        private lateinit var startup: Startup

        fun startInit() {
            startup.start()
        }

        fun cancelInit() {
            startup.cancel()
        }
    }

    override fun onCreate() {
        super.onCreate()

        val currentProcessName = getCurrentProcessName() ?: ""

        val isMainProcess = currentProcessName == packageName

        //进程判断，如果不是主进程，而且也不是我们自己的子进程，不允许初始化
        if (!isMainProcess && !isMyAppProcess(currentProcessName)) {
            return
        }


        //轻量级数据存储推荐直接初始化,用于进行判断逻辑.
        //推荐使用可跨进程的工具 MMKV
        SpUtils.init(this)

        //构建初始化任务列表.
        val initializer = listOf(
            AdMobInit(),
            AppConfigInit(),
            FlutterEngineInit(),
            FistInitializer(),
            IMInit(),
            MapSdkInit(),
            ExceptionInit()
        )
        //构建startup
        startup = Startup.Builder(this)
            .setDispatchers(StartupDispatchers.Default)
            .setDebug(true)
            .add(initializer)
            .build()
        //推荐!!!
        //如果App跳过了手动初始化,就必须做判断,只要条件允许第二次初始化的时候
        //一定在Application中直接执行.
        //这里涉及到了一些进程重启逻辑.
        if (SpUtils.getBoolean("isAgreePrivacy", false)) {
            startInit()
        }
    }

    private fun getCurrentProcessName(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.runningAppProcesses?.find { it.pid == Process.myPid() }?.processName
        }
    }

    /**
     * 如果我们有自己的子进程任务，必须要判断进程是否是自己的。
     * 如果不是自己的进程，不允许进行初始化。
     * 第三方SDK获取会频繁启动子进程进行数据上报或者其他处理，所以第三方子进程在没有明确要求的情况下不让他们进行初始化。
     */
    private fun isMyAppProcess(processName: String): Boolean {
        return processName.contains (":webview")
    }

}

