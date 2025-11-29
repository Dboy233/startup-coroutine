package com.dboy.coroutine

import android.content.Intent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.dboy.coroutine.utils.SpUtils
import com.dboy.startup.coroutine.Startup
import com.dboy.startup.coroutine.model.StartupResult

/**
 * 启动页 / 闪屏页
 *
 * 职责：
 * 1. 处理隐私协议授权流程（合规要求）。
 * 2. 触发并监听 App 全局初始化 [Startup] 的结果。
 * 3. 初始化完成后跳转至主页。
 */
class SplashActivity : BaseActivity(R.layout.activity_splash) {

    private val button by lazy {
        findViewById<Button>(R.id.btn_go_main)
    }

    /**
     * 核心逻辑：监听启动框架的状态变化, 并自定义触发初始化任务
     * 重写自 BaseActivity
     */
    override fun observeStartup() {
        Startup.observe(this) {
            when (it) {
                StartupResult.Idle -> checkPrivacyOrWaitStartupInit()
                is StartupResult.Failure -> {
                    hideLoading()
                    if (isCanIgnoreInitJobError(it)) {
                        onInitFinished()
                    } else {
                        onInitFailed(it)
                    }
                }

                StartupResult.Success -> {
                    hideLoading()
                    Toast.makeText(this, "初始化成功!", Toast.LENGTH_SHORT).show()
                    onInitFinished()
                }
            }
        }
    }

    /**
     * 检查隐私协议状态并决定下一步
     */
    private fun checkPrivacyOrWaitStartupInit() {
        val isAgreed = SpUtils.getBoolean("isAgreePrivacy", false)
        if (isAgreed) {
            showLoading()
        } else {
            // 新用户，显示隐私弹窗
            showPrivacyAgreementDialog()
        }
    }


    override fun initView() {
        button.setOnClickListener {
            // 双重保险：虽然只有 Success 才会调 initView，但再次检查更稳健
            if (Startup.isInitialized()) {
                startActivity(Intent(this, MainActivity::class.java))
                finish() // 销毁 Splash，防止用户按返回键回到这里
            } else {
                // 理论上不应走到这里，除非逻辑有漏洞
                Toast.makeText(this, "正在初始化中...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun initData() {

    }

    override fun onInitFailed(failure: StartupResult.Failure) {
        super.onInitFailed(failure)
        showRestartDialog()
    }

    private fun showRestartDialog() {
        // 示例思路
        AlertDialog.Builder(this)
            .setTitle("错误")
            .setMessage("初始化失败，请检查网络后重试。")
            .setPositiveButton("重试") { _, _ -> App.startInit() } // 重新触发
            .setNegativeButton("退出") { _, _ -> finish() }
            .show()
    }

    private fun showPrivacyAgreementDialog() {
        AlertDialog.Builder(this)
            .setTitle("隐私协议")
            .setMessage("欢迎使用本应用。在使用前，请您仔细阅读并同意我们的隐私政策和用户协议。")
            .setCancelable(false) // 禁止点击外部取消，强制用户选择
            .setPositiveButton("同意") { dialog, which ->
                // 用户点击同意后的逻辑，例如初始化SDK或进入主功能
                dialog.dismiss()
                showLoading()
                Toast.makeText(this, "已同意隐私协议,开始初始化", Toast.LENGTH_SHORT).show()
                SpUtils.putBoolean("isAgreePrivacy", true)
                App.startInit()
            }
            .setNegativeButton("拒绝") { dialog, which ->
                // 用户点击拒绝后的逻辑，通常是退出应用或再次提示
                SpUtils.putBoolean("isAgreePrivacy", false)
                dialog.dismiss()
                Toast.makeText(this, "您拒绝了隐私协议，即将退出应用", Toast.LENGTH_SHORT).show()
                finish() // 退出 Activity
            }
            .show()
    }

}