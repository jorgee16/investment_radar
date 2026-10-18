package com.investmentradar.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Diagnostic control: Android WebView without app assets, bridge, JNI or providers. */
@RunWith(AndroidJUnit4::class)
class WebViewSanityTest {
    @Test fun stockWebViewExecutesJavascript() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready=CountDownLatch(1)
        lateinit var view: WebView
        var result: String?=null
        instrumentation.runOnMainSync {
            view=WebView(instrumentation.targetContext)
            view.settings.javaScriptEnabled=true
            view.webViewClient=object: WebViewClient() {
                override fun onPageFinished(web: WebView,url: String) {
                    web.evaluateJavascript("6*7") { result=it;ready.countDown() }
                }
            }
            view.loadDataWithBaseURL("https://example.invalid/", "<!doctype html><html><body>WebView control</body></html>","text/html","UTF-8",null)
        }
        try {
            assertTrue("Stock WebView did not load/execute JavaScript",ready.await(90,TimeUnit.SECONDS))
            assertEquals("42",result)
        } finally { instrumentation.runOnMainSync { view.destroy() } }
    }
}
