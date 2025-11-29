package com.dboy.coroutine

import android.widget.Button
import com.dboy.startup.coroutine.Startup

class MainActivity : BaseActivity(R.layout.activity_main) {

    private val button by lazy {
        findViewById<Button>(R.id.btn_reinit)
    }

    override fun initView() {
        button.setOnClickListener {
            if (Startup.isInitialized()) {
                Startup.reset()
                App.startInit()
            }
        }
    }


    override fun initData() {
        //do something
    }

}