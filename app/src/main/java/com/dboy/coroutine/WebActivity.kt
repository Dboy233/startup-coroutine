package com.dboy.coroutine

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
class WebActivity : BaseActivity(R.layout.activity_web) {

    private val webView by lazy {
        findViewById<WebView>(R.id.web_view)
    }

    override fun initView() {
        // Basic WebView setup
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
    }

    override fun initData() {
        // This method is called only after the startup process in this process is complete.
        // We load the URL here to demonstrate that the initialization was successful.
        webView.loadUrl("https://bing.com")
    }
}