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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.appcompat.app.AppCompatActivity
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
                    require(operation in setOf("/api/status", "/api/providers", "/api/settings", "/api/quote", "/api/snapshot", "/api/history", "/api/earnings", "/api/news", "/api/providers/test"))
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
                runOnUiThread { if (!isFinishing && !isDestroyed) web.evaluateJavascript("window.nativeResult(${JSONObject.quote(id)},$result)", null) }
            }
        }
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SecureSettings(this); cache = ApiCache(this); http = HttpClient(cache)
        web = WebView(this)
        // Keep WebView inside the system bars and display cutout on Android 15+.
        val container = FrameLayout(this)
        // Android 15 draws transparent system bars over the window background.
        val background = Color.rgb(11, 15, 20)
        window.decorView.setBackgroundColor(background)
        container.setBackgroundColor(background)
        container.addView(web, FrameLayout.LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = safe.left, top = safe.top, right = safe.right, bottom = safe.bottom)
            insets
        }
        setContentView(container)
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web.settings.apply { javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = false; allowContentAccess = false; setSupportMultipleWindows(false) }
        web.webViewClient = object : WebViewClientCompat() {
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
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }
    override fun onDestroy() {
        web.removeJavascriptInterface("AndroidBridge"); web.destroy()
        executor.execute { cache.close() }
        executor.shutdown()
        super.onDestroy()
    }
}
