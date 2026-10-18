package com.investmentradar.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.view.Gravity
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.RenderProcessGoneDetail
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object { init { System.loadLibrary("investment_radar") } }
    external fun nativeRequest(path: String, symbol: String, settings: String): String
    private lateinit var web: WebView
    private lateinit var settings: SecureSettings
    private lateinit var cache: ApiCache
    private lateinit var http: HttpClient
    private var rendererGone = false
    private val executor = Executors.newSingleThreadExecutor()
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
            executor.execute {
                val result = try {
                    val uri = Uri.parse(path)
                    val current = settings.load()
                    val operation = uri.path ?: ""
                    require(operation in setOf("/api/status", "/api/providers", "/api/settings", "/api/quote", "/api/snapshot", "/api/earnings", "/api/news", "/api/providers/test"))
                    var symbol = uri.getQueryParameter("symbol") ?: ""
                    if (operation == "/api/settings" && method == "POST") {
                        val update = JSONObject(body)
                        update.keys().forEach { key -> current.put(key, update.get(key)) }
                        val validation = JSONObject(nativeRequest("/api/validate", "", current.toString()))
                        check(!validation.has("error")) { "Invalid settings" }
                        settings.save(validation.getJSONObject("data"))
                    } else if (operation == "/api/providers/test") {
                        require(method == "POST")
                        // The payload contains only symbol/provider; credentials come from native storage.
                        symbol = JSONObject(body).toString()
                    } else require(method == "GET")
                    nativeRequest(operation, symbol, settings.load().toString())
                } catch (_: Exception) { JSONObject().put("error", "Não foi possível concluir a operação nativa.").toString() }
                runOnUiThread { if (!isFinishing && !isDestroyed && !rendererGone) web.evaluateJavascript("window.nativeResult(${JSONObject.quote(id)},$result)", null) }
            }
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SecureSettings(this); cache = ApiCache(this); http = HttpClient(cache)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(7, 10, 15)) }
        web = WebView(this)
        web.setBackgroundColor(Color.rgb(7, 10, 15))
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = false; allowContentAccess = false; setSupportMultipleWindows(false) }
        web.webViewClient = object : WebViewClientCompat() {
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone = true
                root.removeView(view)
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
                root.addView(recovery, FrameLayout.LayoutParams(-1, -1))
                return true
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
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
        executor.execute { cache.close() }
        executor.shutdown()
        super.onDestroy()
    }
}
