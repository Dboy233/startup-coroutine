package com.dboy.coroutine

import android.content.Intent
import android.widget.Button
import com.dboy.startup.coroutine.Startup

class MainActivity : BaseActivity(R.layout.activity_main) {

    private val reinitButton by lazy {
        findViewById<Button>(R.id.btn_reinit)
    }

    private val webViewButton by lazy {
        findViewById<Button>(R.id.btn_open_webview)
    }

    override fun initView() {
        reinitButton.setOnClickListener {
            if (Startup.isInitialized()) {
                Startup.reset()
                App.startInit()
            }
        }

        webViewButton.setOnClickListener {
            startActivity(Intent(this, WebActivity::class.java))
        }
    }


    override fun initData() {
        //do something
    }

}