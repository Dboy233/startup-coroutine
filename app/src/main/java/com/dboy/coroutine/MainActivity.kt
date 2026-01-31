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

    private val adProcessButton by lazy {
        findViewById<Button>(R.id.btn_open_ad_process)
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

        adProcessButton.setOnClickListener {
            startActivity(Intent(this, AdProcessActivity::class.java))
        }
    }


    override fun initData() {
        //do something
    }

}