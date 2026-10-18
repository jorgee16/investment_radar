package com.investmentradar.app

import android.content.Intent
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the installed APK: real WebView, Kotlin bridge, JNI, SQLite and Keystore.
 * Demo fixtures are explicit; no paid-provider entitlement is simulated here. */
@RunWith(AndroidJUnit4::class)
class AndroidFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var activity: MainActivity
    private lateinit var web: WebView

    @Before fun launch() {
        context.getSharedPreferences("secure_providers", 0).edit().clear().commit()
        context.getSharedPreferences("providers", 0).edit().clear().commit()
        instrumentation.runOnMainSync { WebStorage.getInstance().deleteAllData() }
        start()
    }
    private fun start() {
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync { web = (activity.findViewById<android.widget.FrameLayout>(android.R.id.content).getChildAt(0) as android.widget.FrameLayout).getChildAt(0) as WebView }
        await("demo snapshot loaded") { truth("return document.querySelector('#assetPrice')?.textContent.includes('188') && document.querySelector('#runtime')?.textContent.includes('Android');") }
    }
    @After fun finish() { if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() } }
    private fun eval(script: String): Any? {
        val latch = CountDownLatch(1); var value: Any? = null
        instrumentation.runOnMainSync {
            web.evaluateJavascript("(()=>{$script})()") { raw -> value = JSONTokener(raw ?: "null").nextValue(); latch.countDown() }
        }
        assertTrue("WebView callback timed out", latch.await(45, TimeUnit.SECONDS))
        return value
    }
    private fun truth(script: String) = eval(script) == true
    private fun await(label: String, check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 90000
        do { if (check()) return; SystemClock.sleep(100) } while (SystemClock.uptimeMillis() < end)
        fail("Timed out: $label; notice=" + eval("return document.querySelector('#notice')?.textContent;"))
    }
    private fun tap(selector: String) {
        val quoted = JSONObject.quote(selector)
        eval("document.querySelector($quoted).scrollIntoView({block:'center',inline:'nearest'});")
        val box = eval("const r=document.querySelector($quoted).getBoundingClientRect();return JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth});") as String
        val rect = JSONObject(box); val location = IntArray(2); var ratio = 1f
        instrumentation.runOnMainSync { web.getLocationOnScreen(location); ratio = web.width / rect.getDouble("w").toFloat() }
        val x = location[0] + rect.getDouble("x").toFloat() * ratio
        val y = location[1] + rect.getDouble("y").toFloat() * ratio
        val now = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 30)) {
            val event = MotionEvent.obtain(now, time, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            assertTrue("Touch injection failed: $selector", instrumentation.uiAutomation.injectInputEvent(event, true)); event.recycle()
        }
        instrumentation.waitForIdleSync()
    }
    private fun fill(selector: String, value: String) { eval("const e=document.querySelector(${JSONObject.quote(selector)});e.value=${JSONObject.quote(value)};e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));") }
    private fun view(id: String) {
        if (id in setOf("screener", "providers", "settings")) {
            tap("#moreNav"); await("more menu") { truth("return document.querySelector('#moreDialog').open;") }; tap("[data-open-view='$id']")
        } else tap(".nav[data-view='$id']")
        await("view $id") { truth("return document.querySelector('#$id').classList.contains('active');") }
        if (id == "providers") await("provider form") { truth("return document.querySelector('#providers').getAttribute('aria-busy')==='false';") }
        assertLayout(id)
    }
    private fun assertLayout(label: String) {
        assertTrue("Horizontal page overflow: $label", truth("return document.documentElement.scrollWidth<=innerWidth+1;"))
        assertTrue("Bottom navigation overlapped or outside viewport: $label", truth("const r=document.querySelector('.sidebar').getBoundingClientRect();return r.bottom<=innerHeight+1 && r.top>=0;"))
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        val image = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Screenshot unavailable", image)
        if (Build.VERSION.SDK_INT >= 29) {
            val folder="Pictures/InvestmentRadarValidation/"
            val resolver=context.contentResolver
            resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?", arrayOf("$name.png",folder))
            val values=ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png")
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,folder)
                put(MediaStore.Images.Media.IS_PENDING,1)
            }
            val uri=checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
            checkNotNull(resolver.openOutputStream(uri)).use { assertTrue(image!!.compress(Bitmap.CompressFormat.PNG,100,it)) }
            resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
        } else {
            val folder = File(context.getExternalFilesDir(null), "validation-screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { assertTrue(image!!.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        image!!.recycle()
    }
    private fun back() { instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); instrumentation.waitForIdleSync() }
    private fun setMode(mode: String) {
        fill("#dataMode", mode); tap("#applyDataMode")
        await("mode $mode") { truth("return document.querySelector('#providerModeBadge').textContent==='${mode.uppercase()}' && document.querySelector('#providers').getAttribute('aria-busy')==='false' && document.querySelector('#dashboard').getAttribute('aria-busy')==='false';") }
    }

    @Test fun nativeCoreAndBridge() {
        val config = "{\"demo\":true}"
        val result = JSONObject(activity.nativeRequest("/api/snapshot", "AIR.PA", config)).getJSONObject("data")
        assertEquals(188.0, result.getDouble("price"), 0.001)
        assertEquals("Demo", result.getJSONObject("sources").getString("quote"))
        assertTrue(JSONObject(activity.nativeRequest("/api/unknown", "", config)).has("error"))
        assertTrue(JSONObject(activity.nativeRequest("/api/snapshot", "<script>", config)).has("error"))
        assertTrue(truth("return typeof AndroidBridge.request==='function' && document.querySelector('#settingsRuntime').textContent.includes('Android');"))
        capture("01-dashboard")
    }
    @Test fun allNavigationAndAssetTabs() {
        for (id in listOf("portfolio", "watchlist", "calendar", "screener", "providers", "settings")) { view(id); capture("nav-$id") }
        back(); await("back returns dashboard") { truth("return document.querySelector('#dashboard').classList.contains('active');") }
        for (tab in listOf("financials", "valuation", "earnings", "news", "overview")) {
            tap("[data-tab='$tab']"); await("tab $tab") { truth("return document.querySelector('#tab-$tab').classList.contains('active');") }; assertLayout(tab); capture("tab-$tab")
        }
        tap("#moreNav"); back(); await("back dismisses menu") { truth("return !document.querySelector('#moreDialog').open;") }
    }
    @Test fun watchlistFormsAndPersistence() {
        view("watchlist"); await("watch cards") { truth("return document.querySelectorAll('#watchGrid [data-w]').length===6;") }
        tap("#addWatch"); fill("#entrySymbol", "BAD SYMBOL"); tap("#entrySubmit")
        assertTrue(truth("return document.querySelector('#entryError').textContent.includes('inválido');"))
        fill("#entrySymbol", "AIR.PA"); tap("#entrySubmit")
        assertTrue(truth("return document.querySelector('#entryError').textContent.includes('já');"))
        fill("#entrySymbol", "AAPL"); tap("#entrySubmit")
        await("added ticker") { truth("return document.querySelectorAll('#watchGrid [data-w]').length===7;") }
        tap("#watchGrid [data-w='AAPL']"); await("open added ticker") { truth("return document.querySelector('#assetTicker').textContent==='AAPL' && document.querySelector('#assetName').textContent==='Apple Inc.';") }
        instrumentation.runOnMainSync { activity.finish() }; start(); view("watchlist")
        await("watch persisted after Activity restart") { truth("return !!document.querySelector('#watchGrid [data-w='AAPL']');") }
        tap("#watchGrid [data-w='AAPL'] [data-rw]")
        await("remove ticker") { truth("return !document.querySelector('#watchGrid [data-w='AAPL']') && document.querySelectorAll('#radarWatch [data-w]').length===6;") }
        tap("#addWatch"); back(); if (truth("return document.querySelector('#entryDialog').open;")) back(); assertTrue(truth("return !document.querySelector('#entryDialog').open;"))
    }
    @Test fun portfolioValidationTotalsAndPersistence() {
        view("portfolio"); tap("#addPosition")
        fill("#entrySymbol", "AIR.PA"); fill("#entryQty", "-1"); fill("#entryAvg", "180,50"); fill("#entryCurrency", "EUR"); tap("#entrySubmit")
        assertTrue(truth("return !document.querySelector('#entryError').hidden;")); capture("position-validation")
        fill("#entryQty", "2,5"); tap("#entrySubmit")
        await("position saved and valued") { truth("return document.querySelectorAll('#portfolioTable tbody tr').length===1 && document.querySelector('#pfCurrent').textContent.includes('470');") }
        assertTrue(truth("return document.querySelector('#pfInvested').textContent.includes('451,25') && document.querySelector('#pfPL').textContent.includes('18,75');"))
        instrumentation.runOnMainSync { activity.finish() }; start(); view("portfolio")
        await("position persisted") { truth("return document.querySelectorAll('#portfolioTable tbody tr').length===1;") }; capture("portfolio-with-position")
        tap("#portfolioTable [data-rp]"); await("position removed") { truth("return document.querySelectorAll('#portfolioTable tbody tr').length===0 && document.querySelector('#pfCurrent').textContent==='—';") }
        view("dashboard"); assertTrue(truth("return document.querySelector('#positionTitle').textContent==='Sem posição';"))
    }
    @Test fun demoLiveMissingKeysAndRouting() {
        view("providers"); tap("#testAllProviders")
        await("missing key reports") { truth("return document.querySelector('#providerCards').textContent.split('missing_key').length===4;") }
        setMode("live")
        assertTrue(truth("return document.querySelector('#assetPrice').textContent==='—' && document.querySelector('#assetScore').textContent==='—' && document.querySelectorAll('#priceChart path').length===0;"))
        capture("live-without-keys")
        fill("[data-route='quotes']", "twelve"); tap("#saveRouting")
        await("routing saved") { truth("return document.querySelector('[data-route=quotes]').value==='twelve' && !document.querySelector('#saveRouting').disabled;") }
        instrumentation.runOnMainSync { activity.finish() }
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        instrumentation.runOnMainSync { web = (activity.findViewById<android.widget.FrameLayout>(android.R.id.content).getChildAt(0) as android.widget.FrameLayout).getChildAt(0) as WebView }
        await("live survives restart") { truth("return document.querySelector('#settingsMode')?.textContent==='LIVE' && document.querySelector('#dashboard').getAttribute('aria-busy')==='false';") }
        view("providers"); assertTrue(truth("return document.querySelector('[data-route=quotes]').value==='twelve';")); setMode("demo")
        await("demo restored") { truth("return document.querySelector('#assetPrice').textContent.includes('188');") }
    }
    @Test fun keystoreAndCredentialForm() {
        view("providers"); fill("#keyEodhd", "fixture-not-a-real-api-key"); tap("#saveProviderKeys")
        await("key saved") { truth("return document.querySelector('#keyEodhd').placeholder.includes('Configurado') && !document.querySelector('#saveProviderKeys').disabled;") }
        assertEquals("fixture-not-a-real-api-key", SecureSettings(context).load().getString("eodhdKey"))
        val encrypted = context.getSharedPreferences("secure_providers", 0).getString("config", "")!!
        assertFalse(encrypted.contains("fixture-not-a-real-api-key"))
        val cfg = JSONObject(activity.nativeRequest("/api/settings", "", SecureSettings(context).load().toString())).getJSONObject("data")
        assertFalse(cfg.toString().contains("fixture-not-a-real-api-key")); assertEquals("local", cfg.getJSONObject("keySources").getString("eodhd"))
        assertTrue(truth("return document.querySelector('#keyEodhd').value==='' && document.querySelector('#providerKeyForm').textContent.includes('Chave guardada');"))
        eval("document.querySelector('#clearEodhd').checked=true;"); tap("#saveProviderKeys")
        await("key removed") { truth("return document.querySelector('#keyEodhd').placeholder==='Chave de API' && !document.querySelector('#saveProviderKeys').disabled;") }
        assertEquals("", SecureSettings(context).load().optString("eodhdKey"))
    }
    @Test fun searchScreenerAndKeyboard() {
        tap("#symbolInput"); instrumentation.sendStringSync("JPM"); fill("#symbolInput", "JPM")
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
        await("IME search") { truth("return document.querySelector('#assetTicker').textContent==='JPM' && document.querySelector('#assetName').textContent.includes('JPMorgan');") }
        fill("#symbolInput", "BAD SYMBOL"); tap("#symbolSearch button")
        assertTrue(truth("return document.querySelector('#notice').textContent==='Ticker inválido.';"))
        view("screener"); fill("#screenScore", "40")
        await("screener results") { truth("return document.querySelectorAll('#screenerResults [data-w]').length>0;") }
        fill("#screenZone", "BUY")
        await("screener filter") { truth("return [...document.querySelectorAll('#screenerResults .watch-meta')].every(e=>e.textContent.includes('BUY'));") }
        capture("screener-filtered")
    }
    @Test fun rendererTerminationRecoversWithoutLosingWatchlist() {
        view("watchlist"); tap("#addWatch"); fill("#entrySymbol","AAPL"); tap("#entrySubmit")
        await("watch saved before renderer termination") { truth("return !!document.querySelector(\"#watchGrid [data-w='AAPL']\");") }
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= 29) assertTrue(web.webViewRenderProcess?.terminate() == true)
            else web.loadUrl("chrome://crash")
        }
        var retry: android.widget.Button?=null
        await("native recovery screen") {
            instrumentation.runOnMainSync { retry=activity.findViewById<android.widget.FrameLayout>(android.R.id.content).findViewWithTag("retry-webview") }
            retry != null
        }
        capture("renderer-recovery")
        val old=activity
        instrumentation.runOnMainSync { retry!!.performClick() }
        await("activity recreated") {
            var next: MainActivity?=null
            instrumentation.runOnMainSync {
                next=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull { it !== old }
            }
            if (next != null) {
                activity=next!!
                instrumentation.runOnMainSync { web=(activity.findViewById<android.widget.FrameLayout>(android.R.id.content).getChildAt(0) as android.widget.FrameLayout).getChildAt(0) as WebView }
                true
            } else false
        }
        await("snapshot restored after renderer termination") { truth("return document.querySelector('#assetPrice')?.textContent.includes('188');") }
        view("watchlist"); await("watchlist preserved after renderer termination") { truth("return !!document.querySelector(\"#watchGrid [data-w='AAPL']\");") }
    }
    @Test fun sqliteCacheAndTransportPolicy() {
        ApiCache(context).use { cache ->
            val url="https://eodhd.com/api/quote?api_token=cache-fixture"
            cache.put(url,"{\"close\":123}")
            assertNotNull(cache.get(url,60)); assertNull(cache.get(url,0))
            cache.readableDatabase.rawQuery("SELECT cache_key FROM http_cache_v2",null).use { c -> assertTrue(c.moveToFirst()); assertEquals(64,c.getString(0).length); assertFalse(c.getString(0).contains("cache-fixture")) }
            try { HttpClient(cache).get("http://eodhd.com/api/quote"); fail("Cleartext should be rejected") } catch (_: IllegalArgumentException) { }
            try { HttpClient(cache).get("https://example.org/api/quote"); fail("Unexpected host should be rejected") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun rotationRetainsNavigationAndSafeInsets() {
        view("watchlist")
        instrumentation.runOnMainSync { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        await("landscape") { truth("return innerWidth>innerHeight;") }
        assertTrue(truth("return document.querySelector('#watchlist').classList.contains('active');")); assertLayout("landscape"); capture("landscape-watchlist")
        instrumentation.runOnMainSync { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        await("portrait") { truth("return innerHeight>innerWidth;") }; assertLayout("portrait"); capture("portrait-watchlist")
        tap("#addWatch"); tap("#entrySymbol"); capture("keyboard-and-dialog"); back()
    }
}
