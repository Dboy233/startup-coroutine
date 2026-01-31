package com.dboy.coroutine

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 该Activity运行在一个独立进程中，该进程未在 App.kt 中被列入白名单。
 * 它用于测试启动初始化是否被正确绕过。
 */
class AdProcessActivity : AppCompatActivity(R.layout.activity_ad_process) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 查看启动状态。由于该流程未在App.kt中列入白名单，
        // “startup”变量本不应该被初始化，调用尚未初始化的lateinit变量会抛出异常。
        // 我们可以用尝试try-catch来证明这一点。
        var msg: String
        try {
            // 如果启动程序未被创建，该行将抛出UninitializedPropertyAccessException。
            val startup = App.startup
            msg = "广告进程: Startup 初始化 = $startup (Bypass逻辑失败)"
        } catch (e: UninitializedPropertyAccessException) {
            msg = "广告进程: Startup 未被初始化 (Bypass逻辑成功!)"
        }

        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}