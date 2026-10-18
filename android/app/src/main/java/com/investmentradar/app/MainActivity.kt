package com.investmentradar.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.view.Gravity
import android.webkit.RenderProcessGoneDetail
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

class MainActivity : AppCompatActivity() {
    companion object {
        private val configLock = Any()
        init { System.loadLibrary("investment_radar") }
    }
    external fun nativeRequest(path: String, symbol: String, settings: String): String
    private lateinit var web: WebView
    private lateinit var settings: SecureSettings
    private lateinit var cache: ApiCache
    private lateinit var http: HttpClient
    private var rendererGone = false
    private val executor = object : ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>()) {
        override fun terminated() { if (this@MainActivity::cache.isInitialized) cache.close() }
    }
    // Called synchronously from JNI on the background request executor, never from WebView JavaScript.
    fun fetchUrl(url: String, bypassCache: Boolean): String = try {
        JSONObject().put("body", http.get(url, bypassCache)).toString()
    } catch (e: ProviderHttpException) {
        JSONObject().put("httpStatus", e.status).toString()
    } catch (_: Exception) {
        JSONObject().put("error", "network").toString()
    }
    inner class Bridge {
        @JavascriptInterface fun request(id: String, path: String, method: String, body: String) {
            if (id.length > 32 || path.length > 2048 || body.length > 16384) return
            try { executor.execute {
                val result = try {
                    val uri = Uri.parse(path)
                    val operation = uri.path ?: ""
                    require(operation in setOf("/api/status", "/api/providers", "/api/settings", "/api/quote", "/api/snapshot", "/api/history", "/api/earnings", "/api/news", "/api/providers/test"))
                    var symbol = uri.getQueryParameter("symbol") ?: ""
                    if (operation == "/api/history") {
                        val days = uri.getQueryParameter("days")?.toIntOrNull()?.coerceIn(1, 5000) ?: 120
                        symbol = JSONObject().put("symbol", symbol).put("days", days).toString()
                    }
                    if (operation == "/api/providers/test") {
                        require(method == "POST")
                        symbol = JSONObject(body).toString()
                    } else require(method == "GET" || (operation == "/api/settings" && method == "POST"))
                    val config = synchronized(configLock) {
                        if (operation == "/api/settings" && method == "POST") {
                            val current = settings.load()
                            val update = JSONObject(body)
                            update.keys().forEach { key -> current.put(key, update.get(key)) }
                            val validation = JSONObject(nativeRequest("/api/validate", "", current.toString()))
                            check(!validation.has("error")) { "Invalid settings" }
                            settings.save(validation.getJSONObject("data"))
                        }
                        settings.load().toString()
                    }
                    nativeRequest(operation, symbol, config)
                } catch (_: Exception) { JSONObject().put("error", "Não foi possível concluir a operação nativa.").toString() }
                runOnUiThread { if (!isFinishing && !isDestroyed && !rendererGone) web.evaluateJavascript("window.nativeResult(${JSONObject.quote(id)},$result)", null) }
            } } catch (_: RejectedExecutionException) { /* Page is already closing. */ }
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SecureSettings(this); cache = ApiCache(this); http = HttpClient(cache)
        web = WebView(this)
        web.setBackgroundColor(Color.rgb(11, 15, 20))
        // Keep WebView inside the system bars and display cutout on Android 15+.
        val container = FrameLayout(this)
        // Android 15 draws transparent system bars over the window background.
        val background = Color.rgb(11, 15, 20)
        window.decorView.setBackgroundColor(background)
        container.setBackgroundColor(background)
        container.addView(web, FrameLayout.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.updatePadding(left = safe.left, top = safe.top, right = safe.right, bottom = safe.bottom)
            insets
        }
        setContentView(container)
        WindowInsetsControllerCompat(window, container).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.requestApplyInsets(container)
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = false; allowContentAccess = false; setSupportMultipleWindows(false) }
        web.webViewClient = object : WebViewClientCompat() {
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone = true
                container.removeView(view)
                view.removeJavascriptInterface("AndroidBridge")
                view.destroy()
                val recovery = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    val padding = (24 * resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                    addView(TextView(context).apply {
                        setText(R.string.webview_failure); setTextColor(Color.WHITE); textSize = 18f
                        gravity = Gravity.CENTER
                    })
                    addView(Button(context).apply {
                        setText(R.string.webview_retry); tag = "retry-webview"
                        setOnClickListener { recreate() }
                    })
                }
                container.addView(recovery, FrameLayout.LayoutParams(-1, -1))
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url != "https://appassets.androidplatform.net/assets/index.html") return
                // Load the phone-specific presentation without changing desktop assets or native APIs.
                view.evaluateJavascript(
                    """(function(){
                      if(document.getElementById('mobile-nav-js'))return;
                      for(const name of ['mobile.css','mobile-cards.css','mobile-dialogs.css']){
                        const link=document.createElement('link');link.rel='stylesheet';link.href=name;document.head.appendChild(link);
                      }
                      for(const name of ['mobile-nav.js','mobile-position.js','mobile-watch.js']){
                        const script=document.createElement('script');script.id=name.replace('.','-');script.src=name;script.async=false;document.body.appendChild(script);
                      }
                    })();""".trimIndent(), null
                )
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == "appassets.androidplatform.net" &&
                    uri.path?.startsWith("/assets/") == true) return false
                // External article links open in the system browser, never in this privileged WebView.
                if (ProviderPolicy.mayOpenExternal(uri.scheme, uri.host,
                        request.isForMainFrame, request.hasGesture())) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }
                return true
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                loader.shouldInterceptRequest(request.url) ?: WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(byteArrayOf()))
        }
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (rendererGone) { finish(); return }
                web.evaluateJavascript("Boolean(window.handleNativeBack && window.handleNativeBack())") { handled ->
                    if (handled != "true" && !isFinishing) finish()
                }
            }
        })
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }
    override fun onDestroy() {
        if (!rendererGone) { web.removeJavascriptInterface("AndroidBridge"); web.destroy() }
        executor.shutdown()
        super.onDestroy()
    }
}
