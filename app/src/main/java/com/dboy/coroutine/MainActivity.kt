package com.dboy.coroutine

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dboy.coroutine.startup.ExceptionInit
import com.dboy.startup.coroutine.Startup
import com.dboy.startup.coroutine.model.StartupResult
import com.google.android.material.loadingindicator.LoadingIndicator

class MainActivity : AppCompatActivity() {

    private val button by lazy {
        findViewById<Button>(R.id.btn_reinit)
    }

    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Startup.observe(this) {
            when (it) {
                StartupResult.Idle -> {
                    if (SpUtils.getBoolean("isAgreePrivacy", false)) {
                        showLoading()
                    } else {
                        showPrivacyAgreementDialog()
                    }
                    Toast.makeText(this, "未初始化.", Toast.LENGTH_SHORT).show()
                }

                is StartupResult.Failure -> {
                    hideLoading()
                    it.exceptions.forEach { error ->
                        if (error.initializerClass == ExceptionInit::class) {
                            Toast.makeText(this, "初始化失败,但是无关紧要.", Toast.LENGTH_SHORT)
                                .show()
                            initUI()
                            initData()
                        }
                    }
                }

                StartupResult.Success -> {
                    hideLoading()
                    Toast.makeText(this, "初始化成功!", Toast.LENGTH_SHORT).show()
                    initUI()
                    initData()
                }
            }
        }
    }

    private fun showLoading() {
        if (loadingDialog == null) {
            loadingDialog = AlertDialog.Builder(this)
                .setView(LoadingIndicator(this))
                .setMessage("正在初始化...")
                .setCancelable(false) // 加载中不允许点击外部取消
                .create()
        }
        if (loadingDialog?.isShowing == false) {
            loadingDialog?.show()
        }
    }

    private fun hideLoading() {
        if (loadingDialog?.isShowing == true) {
            loadingDialog?.dismiss()
        }
    }

    private fun initUI() {
        button.setOnClickListener {
            if (Startup.isInitialized()){
                Startup.reset()
                App.startInit()
            }
        }
    }


    private fun initData() {
        //do something
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