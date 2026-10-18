package com.investmentradar.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream

/** One-time upgrade path only. No financial screen is rendered in a WebView. */
internal class LegacyPortfolioMigration(private val context: Context, private val done: (JSONObject) -> Unit, private val failed: () -> Unit) {
    private var completed = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var web: WebView
    private val timeout = Runnable { finish(null) }
    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        try {
            web = WebView(context)
            web.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = false; allowContentAccess = false; blockNetworkLoads = true }
            web.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript("JSON.stringify({watch:JSON.parse(localStorage.getItem('ir_watch')||'null'),portfolio:JSON.parse(localStorage.getItem('ir_portfolio')||'null')})") { raw ->
                        val data = runCatching { JSONObject(JSONTokener(raw).nextValue() as String) }.getOrNull()
                        finish(data)
                    }
                }
            }
            handler.postDelayed(timeout, 20000)
            web.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/index.html", "<!doctype html><html></html>", "text/html", "UTF-8", null)
        } catch (_: Exception) { finish(null) }
    }
    private fun finish(data: JSONObject?) {
        if (completed) return
        completed = true; handler.removeCallbacks(timeout)
        if (::web.isInitialized) web.destroy()
        if (data != null) done(data) else failed()
    }
}
