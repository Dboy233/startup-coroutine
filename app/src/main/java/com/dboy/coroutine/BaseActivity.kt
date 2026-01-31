package com.dboy.coroutine

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dboy.coroutine.startup.ExceptionInit
import com.dboy.startup.coroutine.Startup
import com.dboy.startup.coroutine.model.StartupResult
import com.google.android.material.loadingindicator.LoadingIndicator
import kotlin.reflect.KClass

/**
 * 推荐当前BaseActivity的使用方式,原因如下:
 *    App进程由于内存不足被Kill的时候,再次回到app页面,整个进程会被重建.
 *    并且,系统会重建Application和最后的Activity.
 *    此时,activity可能在onCreate阶段调用了某些还为初始化完成的对象.
 *    所以需要同时监听Startup的初始化任务.这样就避免了一些不必要的麻烦.
 *
 *    我们通过初始化状态主动控制loading,这样就能保证UI的流畅过度.
 */
abstract class BaseActivity(layoutId: Int) : AppCompatActivity(layoutId) {

    private var loadingDialog: AlertDialog? = null

    /**
     * 哪些初始化任务允许忽略
     */
    private val ignoreErrorInitJobList = setOf<KClass<*>>(
        ExceptionInit::class
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        observeStartup()
    }

    abstract fun initView()

    abstract fun initData()

    /**
     * 观察Startup的初始化任务.
     * 特定场合,允许子类重写,例如Splash Activity.需要主动控制初始化的时候.
     */
    protected open fun observeStartup() {
        Startup.observe(this) { result ->
            when (result) {
                StartupResult.Idle -> {
                    // 只有当确认 start() 已经调用但还没结果时才显示
                    showLoading()
                }

                is StartupResult.Failure -> {

                    hideLoading()
                    if (isCanIgnoreInitJobError(result)) {
                        onInitFinished()
                    } else {
                        onInitFailed(result)
                    }
                }

                StartupResult.Success -> {
                    hideLoading()
                    onInitFinished()
                }
            }
        }
    }

    protected open fun onInitFinished() {
        initView()
        initData()
    }

    /**
     * 处理严重失败，允许子类重写以自定义错误页面
     */
    protected open fun onInitFailed(failure: StartupResult.Failure) {
        failure.exceptions.forEach {
            Log.e("StartupCoroutine", it.exception.toString())
        }
        Toast.makeText(this, "核心组件初始化失败，请重启应用", Toast.LENGTH_LONG).show()
    }


    /**
     * 判断是否允许忽略初始化错误
     */
    protected fun isCanIgnoreInitJobError(failure: StartupResult.Failure): Boolean {
        if (failure.exceptions.isEmpty()) return true

        // 使用 Kotlin 集合操作符 all 检查是否所有发生异常的类都属于可忽略列表
        return failure.exceptions.all { exception ->
            ignoreErrorInitJobList.contains(exception.initializerClass)
        }
    }


    protected fun showLoading() {
        if (loadingDialog == null) {
            loadingDialog = AlertDialog.Builder(this)
                .setView(LoadingIndicator(this))
                .setMessage("正在初始化...")
                .setPositiveButton("取消") { p0, p1 ->
                    App.cancelInit()
                }
                .setCancelable(false) // 加载中不允许点击外部取消
                .create()
        }
        if (loadingDialog?.isShowing == false) {
            loadingDialog?.show()
        }
    }

    protected fun hideLoading() {
        if (loadingDialog?.isShowing == true) {
            loadingDialog?.dismiss()
        }
    }

}