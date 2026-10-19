package com.investmentradar.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Installed Release APK tests: Compose semantics/touch, real JNI, SQLite and Keystore.
 * No network entitlement is simulated. All market values in Demo are explicitly synthetic. */
@RunWith(AndroidJUnit4::class)
class AndroidFlowTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private fun state() = activity.model.state.value
    @Before fun launch() {
        listOf("secure_providers", "providers", "radar_native", "radar_appearance", "quote_polling").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
        ApiCache(context).use { cache -> listOf("quote_values", "quote_budget", "quote_backoff").forEach { cache.writableDatabase.delete(it, null, null) } }
        LocalPortfolio(context).write(defaultWatch, emptyList(), true)
        start()
    }
    private fun start() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        scenario.onActivity { activity = it }
        await("native data initialized") { state().ready && state().snapshot != null && !state().loading }
    }
    @After fun close() {
        if (::scenario.isInitialized) { scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }; scenario.close() }
    }
    private fun await(label: String, predicate: () -> Boolean) {
        try { ui.waitUntil(90000, predicate) } catch (e: Throwable) {
            runCatching { capture("failure-" + label.replace(Regex("[^a-zA-Z0-9]+"), "-")) }
            val s = state()
            throw AssertionError("$label; page=${s.page}; symbol=${s.symbol}; days=${s.days}; history=${s.history.size}; loading=${s.loading}/${s.historyLoading}; error=${s.error}", e)
        }
    }
    private fun click(tag: String) {
        val node = ui.onNodeWithTag(tag)
        // Scroll only when offscreen; dialog and navigation nodes have no scroll parent.
        runCatching { node.assertIsDisplayed() }.onFailure { node.performScrollTo() }
        node.assertIsDisplayed().performClick()
        ui.waitForIdle()
    }
    private fun fill(tag: String, value: String) { ui.onNodeWithTag(tag).performTextReplacement(value) }
    private fun page(page: Page) {
        if (page in listOf(Page.Home, Page.Markets, Page.Portfolio)) click("nav-${page.name}")
        else { click("nav-More"); click("menu-${page.name}") }
        ui.onNodeWithTag("screen-${page.name}").assertIsDisplayed()
        assertEquals(page, state().page)
    }
    private fun back() { ui.waitForIdle(); instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); instrumentation.waitForIdleSync(); ui.waitForIdle() }
    private fun restart() { scenario.close(); start() }
    private fun search(value: String) { click("open-search"); fill("search-input", value); click("search-submit") }
    private fun capture(name: String, expectLandscape: Boolean? = null) {
        ui.waitForIdle()
        val frames = CountDownLatch(1)
        instrumentation.runOnMainSync {
            activity.window.decorView.postOnAnimation { activity.window.decorView.postOnAnimation { frames.countDown() } }
        }
        assertTrue("Screenshot requires completed display frames", frames.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300) // Allow SurfaceFlinger to present the committed Compose frame.
        var image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        if (expectLandscape != null) {
            val deadline = SystemClock.uptimeMillis() + 10000
            while ((image.width > image.height) != expectLandscape && SystemClock.uptimeMillis() < deadline) {
                image.recycle(); SystemClock.sleep(300)
                image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            }
            assertEquals("Screenshot must show the committed display orientation", expectLandscape, image.width > image.height)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver; val folder = "Pictures/InvestmentRadarValidation/"
            resolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?", arrayOf("$name.png", folder))
            val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, folder); put(MediaStore.Images.Media.IS_PENDING, 1) }
            val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            checkNotNull(resolver.openOutputStream(uri)).use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } else {
            val directory = File(context.getExternalFilesDir(null), "validation-screenshots").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        image.recycle()
    }
    /** Cached HTTP fixtures exercise the real JNI/provider pipeline without an account or network. */
    private fun liveFixture(etf: Boolean) {
        scenario.close()
        val key = if (etf) "fixture-etf-profile" else "fixture-permission-denied"
        LocalPortfolio(context).write(listOf("VUAA.DE"), emptyList(), true)
        val quote = """{"close":132.8,"currency":"EUR","name":"VUAA.DE"}"""
        val denied = """{"status":"error","code":403}"""
        val profile = """{"General":{"Type":"ETF","Name":"Vanguard S&P 500 UCITS ETF","ISIN":"IE00BFMXXD54"},"ETF_Data":{"Company_Name":"Vanguard","Domicile":"Ireland","Index_Name":"S&P 500"}}"""
        val cache = ApiCache(context)
        val root = "https://eodhd.com/api/"
        cache.put(root + "real-time/VUAA.XETRA?api_token=$key&fmt=json", quote)
        cache.put(root + "search/VUAA.DE?api_token=$key&fmt=json&limit=5", "[]")
        val url = root + "v1.1/fundamentals/VUAA.XETRA?api_token=$key&fmt=json"
        cache.put(url, if (etf) profile else denied)
        cache.put("$url&filter=General", if (etf) """{"Type":"ETF"}""" else denied)
        cache.put("$url&filter=Earnings", denied)
        val history = """[{"date":"2026-06-16","close":100},{"date":"2026-07-16","close":95},{"date":"2026-10-16","close":132.8}]"""
        for (range in listOf(30L, 90L, 120L)) for (offset in -1L..1L) {
            val date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(range).plusDays(offset)
            cache.put(root + "eod/VUAA.XETRA?api_token=$key&fmt=json&period=d&from=$date", history)
        }
        cache.put(root + "real-time/AIR.PA?api_token=$key&fmt=json", """{"close":188,"currency":"EUR","name":"Airbus"}""")
        val air = root + "v1.1/fundamentals/AIR.PA?api_token=$key&fmt=json"
        cache.put(air, "{}"); cache.put("$air&filter=Earnings", "{}"); cache.put("$air&filter=General", "{}")
        for (range in listOf(90L, 120L)) for (offset in -1L..1L) {
            val date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(range).plusDays(offset)
            cache.put(root + "eod/AIR.PA?api_token=$key&fmt=json&period=d&from=$date", "[]")
        }
        cache.close()
        QuotePollingSettings(context).enabled = false // Fixtures exercise snapshot HTTP cache; polling requires real transport.
        SecureSettings(context).save(JSONObject().put("demo", false).put("eodhdKey", key))
        start()
        search("VUAA.DE")
        await("fixture loaded") { !state().loading && !state().historyLoading && state().snapshot?.text("symbol") == "VUAA.DE" }
        assertFalse(state().demo)
    }
    @Test fun missingFundamentalsExplainPermissionsAndKeepPriceAnalysis() {
        liveFixture(false)
        assertEquals(132.8, state().snapshot!!.getDouble("price"), .001)
        click("asset-tab-1")
        ui.onAllNodesWithText("EODHD: O plano ou as permissões não permitem este pedido.").assertCountEquals(0)
        ui.onNodeWithText("COMPONENTES DO SCORE").assertDoesNotExist()
        capture("live-permission-analysis")
        ui.onNodeWithText("COMPORTAMENTO DO PREÇO").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("+32,80%").performScrollTo().assertIsDisplayed()
        capture("live-price-analysis")
        click("analytics-drawdown")
        ui.onNodeWithTag("analytics-chart-mode").assertTextEquals("Queda desde o pico anterior (%)")
        capture("live-price-drawdown")
        click("availability-details")
        ui.onAllNodesWithText("EODHD: O plano ou as permissões não permitem este pedido.").onFirst().performScrollTo().assertIsDisplayed()
        capture("live-permission-expanded")
        click("asset-tab-2")
        ui.onNodeWithText("P/E").assertDoesNotExist()
        capture("live-permission-financials")
        click("availability-providers")
        assertEquals(Page.Providers, state().page)
    }
    @Test fun etfProfileAndAnalysisUseFundDataInsteadOfCorporateScore() {
        liveFixture(true)
        assertEquals("etf", state().snapshot!!.text("instrumentType"))
        click("asset-tab-1")
        capture("live-etf-analysis-top")
        ui.onNodeWithText("ANÁLISE DE ETF / FUNDO").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Score heurístico").assertDoesNotExist()
        ui.onNodeWithText("INTERVALO DE VALOR JUSTO").assertDoesNotExist()
        capture("live-etf-analysis")
        click("asset-tab-2")
        ui.onNodeWithText("PERFIL DO ETF / FUNDO").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("IE00BFMXXD54").assertExists()
        ui.onNodeWithText("S&P 500").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Retorno sobre o capital (ROE)").assertDoesNotExist()
        capture("live-etf-profile")
    }
    @Test fun nativeCoreAndNativePresentation() {
        val core = NativeCore(context)
        try {
            val result = JSONObject(core.nativeRequest("/api/snapshot", "AIR.PA", "{\"demo\":true}")).getJSONObject("data")
            assertEquals(188.0, result.getDouble("price"), .001); assertEquals("Demo", result.getJSONObject("sources").getString("quote"))
            assertTrue(JSONObject(core.nativeRequest("/api/unknown", "", "{}")).has("error"))
            assertTrue(JSONObject(core.nativeRequest("/api/snapshot", "<script>", "{}")).has("error"))
        } finally { core.close() }
        ui.onNodeWithTag("native-app").assertExists(); capture("01-home")
        page(Page.Markets); ui.onNodeWithTag("asset-price").assertTextContains("188", substring = true)
        ui.onNodeWithTag("price-chart").performScrollTo().assertIsDisplayed(); capture("02-asset-summary")
        var webCount = 0
        instrumentation.runOnMainSync {
            fun count(view: android.view.View) { if (view is WebView) webCount++; if (view is android.view.ViewGroup) for (i in 0 until view.childCount) count(view.getChildAt(i)) }
            count(activity.window.decorView)
        }
        assertEquals("Financial screens must be native Compose", 0, webCount)
    }
    @Test fun trailingEpsProducesPeInSummaryAndFinancials() {
        scenario.close()
        val key = "fixture-trailing-eps"
        val root = "https://eodhd.com/api/"
        val url = root + "v1.1/fundamentals/AIR.PA?api_token=$key&fmt=json"
        val cache = ApiCache(context)
        cache.put(root + "real-time/AIR.PA?api_token=$key&fmt=json", """{"close":188,"currency":"EUR","name":"Airbus"}""")
        cache.put(url, """{"General":{"Type":"Common Stock","Name":"Airbus"},"Highlights":{"DilutedEpsTTM":5,"MostRecentQuarter":"2026-06-30"},"Financials":{"Income_Statement":{"currency_symbol":"EUR"}}}""")
        cache.put("$url&filter=Earnings", "{}")
        for (range in listOf(90L, 120L)) for (offset in -1L..1L) {
            val date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(range).plusDays(offset)
            cache.put(root + "eod/AIR.PA?api_token=$key&fmt=json&period=d&from=$date", "[]")
        }
        cache.close()
        QuotePollingSettings(context).enabled = false // Fixtures exercise snapshot HTTP cache; polling requires real transport.
        SecureSettings(context).save(JSONObject().put("demo", false).put("eodhdKey", key))
        start()
        page(Page.Markets)
        assertFalse(state().demo)
        assertEquals(37.6, state().snapshot!!.getDouble("pe"), .001)
        assertTrue(state().snapshot!!.isNull("forwardPE"))
        ui.onNodeWithText("37,60").performScrollTo().assertIsDisplayed()
        capture("live-trailing-pe-summary-fixture")
        click("asset-tab-2")
        ui.onNodeWithText("37,60").performScrollTo().assertIsDisplayed()
        capture("live-trailing-pe-financials-fixture")
    }
    @Test fun liveProvidersCombineMetricsAndKeepAllAssetTabsFunctional() {
        scenario.close()
        val key = "fixture-combined-providers"
        val cache = ApiCache(context)
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        val eod = "https://eodhd.com/api/"
        val denied = """{"status":"error","code":403}"""
        LocalPortfolio(context).write(listOf("AAPL"), emptyList(), true)
        for (ticker in listOf("AIR.PA", "AAPL")) {
            val code = if (ticker == "AAPL") "AAPL.US" else ticker
            val currency = if (ticker == "AAPL") "USD" else "EUR"
            val price = if (ticker == "AAPL") 343.46 else 188.0
            cache.put(eod + "real-time/$code?api_token=$key&fmt=json", """{"close":$price,"currency":"$currency","name":"Ativo de teste $ticker"}""")
            val fundamental = eod + "v1.1/fundamentals/$code?api_token=$key&fmt=json"
            cache.put(fundamental, denied); cache.put("$fundamental&filter=Earnings", denied)
            val finnhub = "https://finnhub.io/api/v1/"
            cache.put(finnhub + "stock/profile2?symbol=$ticker&token=$key", """{"name":"Ativo de teste $ticker","currency":"$currency"}""")
            cache.put(finnhub + "stock/metric?symbol=$ticker&metric=all&token=$key", """{"metric":{"peTTM":30,"revenueGrowthTTMYoy":12,"epsGrowthTTMYoy":15,"roeTTM":18,"debtEquityAnnual":0.5}}""")
            cache.put(finnhub + "stock/earnings?symbol=$ticker&limit=4&token=$key", """[{"period":"2026-06-30","actual":3,"estimate":2.5}]""")
            val fmp = "https://financialmodelingprep.com/stable/"
            for ((endpoint, payload) in mapOf(
                "ratios-ttm" to """[{"priceToEarningsRatioTTM":28}]""",
                "key-metrics-ttm" to "[]",
                "income-statement-ttm" to """[{"date":"2026-06-30","revenue":1000}]""",
                "cash-flow-statement-ttm" to """[{"date":"2026-06-30","freeCashFlow":120}]"""
            )) cache.put(fmp + "$endpoint?symbol=$ticker&apikey=$key", payload)
            cache.put(fmp + "income-statement?symbol=$ticker&period=annual&limit=5&apikey=$key", """[
                {"date":"2025-12-31","reportedCurrency":"$currency","revenue":1500000000,"netIncome":-150000000},
                {"date":"2024-12-31","reportedCurrency":"$currency","revenue":1200000000,"netIncome":0},
                {"date":"2023-12-31","reportedCurrency":"$currency","revenue":1000000000}
            ]""")
            for (range in listOf(90L, 120L)) for (offset in -1L..1L) {
                cache.put(eod + "eod/$code?api_token=$key&fmt=json&period=d&from=${today.minusDays(range).plusDays(offset)}", """[{"date":"2026-06-30","close":100,"adjusted_close":50,"open":95,"high":110,"low":90},{"date":"2026-10-16","close":$price,"open":180,"high":350,"low":175}]""")
            }
        }
        val eventDate = today.plusDays(5)
        for (fromOffset in -1L..1L) for (toOffset in -1L..1L) {
            val from = today.minusDays(7).plusDays(fromOffset); val to = today.plusDays(90).plusDays(toOffset)
            cache.put(eod + "calendar/earnings?api_token=$key&fmt=json&from=$from&to=$to&symbols=AAPL.US", denied)
            cache.put("https://finnhub.io/api/v1/calendar/earnings?from=$from&to=$to&token=$key&symbol=AAPL", """{"earningsCalendar":[{"date":"$eventDate","symbol":"AAPL","epsEstimate":3}]}""")
        }
        cache.put(eod + "news?api_token=$key&fmt=json&s=AAPL.US&limit=10", """[{"date":"$today","title":"Notícia de teste dos providers","link":"https://example.org/investment-radar-fixture"}]""")
        cache.close()
        QuotePollingSettings(context).enabled = false // Fixtures exercise snapshot HTTP cache; polling requires real transport.
        SecureSettings(context).save(JSONObject().put("demo", false).put("eodhdKey", key).put("finnhubKey", key).put("fmpKey", key))
        start(); search("AAPL")
        await("combined provider data") { !state().loading && !state().historyLoading && state().snapshot?.text("symbol") == "AAPL" && state().history.size == 2 }
        val snapshot = state().snapshot!!
        assertFalse(state().demo)
        assertEquals(30.0, snapshot.getDouble("pe"), .001)
        assertEquals(12.0, snapshot.getDouble("fcfMargin"), .001)
        assertEquals("Finnhub", snapshot.getJSONObject("metricSources").getJSONObject("pe").getString("source"))
        assertEquals("FMP", snapshot.getJSONObject("metricSources").getJSONObject("fcfMargin").getString("source"))
        assertTrue(snapshot.number("score") != null)
        assertTrue(snapshot.isNull("forwardPE"))
        assertEquals(50.0, state().history.first().getDouble("close"), .001)
        ui.onNodeWithText("Velas").performScrollTo().assertIsEnabled().performClick()
        ui.onNodeWithContentDescription("Gráfico de preços com 2 observações, de 45,00 a 350,00. Velas OHLC.").assertExists()
        ui.onNodeWithTag("price-chart").performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("Gráfico de preços com 2 observações, de 45,00 a 350,00. Velas OHLC.").performTouchInput { click(Offset(width * .75f, height * .5f)) }
        ui.onNodeWithText("2026-10-16 · 343,46 USD").assertExists()
        capture("live-combined-candles-fixture")
        ui.onNodeWithText("30,00").performScrollTo().assertIsDisplayed(); capture("live-combined-pe-summary-fixture")
        click("asset-tab-1"); ui.onNodeWithText("Score heurístico").assertExists(); capture("live-combined-analysis-fixture")
        click("asset-tab-2")
        ui.onNodeWithTag("financial-quarterly").assertIsNotEnabled()
        ui.onNodeWithText("-150,0 M").assertExists()
        click("financial-period-0"); ui.onNodeWithTag("financial-netIncome-value").assertTextEquals("—")
        ui.onNodeWithTag("financial-selected-period").assertTextEquals("2023-12-31")
        capture("live-statement-missing-value-fixture")
        click("financial-period-1"); ui.onNodeWithTag("financial-netIncome-value").assertTextEquals("0,00")
        capture("live-statement-zero-value-fixture")
        click("financial-period-2"); capture("live-statement-negative-value-fixture")
        ui.onNodeWithText("30,00").performScrollTo().assertIsDisplayed(); capture("live-combined-financials-fixture")
        click("asset-tab-3"); await("live calendar fallback") { !state().auxiliaryLoading && state().events.isNotEmpty() }
        assertEquals("Finnhub", state().events.single().text("source")); assertNull(state().events.single().number("epsActual")); capture("live-combined-results-fixture")
        click("asset-tab-4"); await("live news") { !state().auxiliaryLoading && state().news.isNotEmpty() }
        ui.onNodeWithText("Notícia de teste dos providers").assertIsDisplayed(); capture("live-combined-news-fixture")
    }
    @Test fun interactiveFinancialChartsSwitchFrequencyAndInspectPeriods() {
        page(Page.Markets); click("asset-tab-2")
        ui.onNodeWithTag("financial-history").assertExists()
        ui.onNodeWithTag("financial-selected-period").assertTextEquals("2025-12-31")
        capture("visual-financial-annual")
        click("financial-quarterly")
        ui.onNodeWithTag("financial-selected-period").assertTextEquals("2025-12-28")
        click("financial-period-0")
        ui.onNodeWithTag("financial-selected-period").assertTextEquals("2025-03-28")
        capture("visual-financial-quarterly")
        ui.onNodeWithTag("financial-chart").performScrollTo().performTouchInput { click(Offset(width * .65f, height * .4f)) }
        ui.onNodeWithTag("financial-selected-period").assertTextEquals("2025-09-28")
        ui.onNodeWithTag("earnings-history").performScrollTo()
        click("earnings-period-0")
        ui.onNodeWithTag("earnings-selected-period").assertTextEquals("2025-03-28")
        capture("visual-eps-comparison")
        click("metric-pe")
        ui.onNodeWithTag("metric-close").assertIsDisplayed(); capture("visual-metric-details"); click("metric-close")
        orient(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        click("financial-annual"); ui.onNodeWithTag("financial-selected-period").assertTextEquals("2025-12-31")
        capture("visual-financial-landscape", true)
    }
    @Test fun visualAnalysisSeparatesMarketModelAndAnalystConsensus() {
        page(Page.Markets); click("asset-tab-1")
        ui.onNodeWithTag("analysis-score").assertExists(); capture("visual-model-score")
        click("analysis-analysts")
        ui.onNodeWithTag("analyst-consensus").assertExists()
        ui.onNodeWithText("COMPONENTES DO SCORE").assertDoesNotExist()
        capture("visual-analyst-distribution")
        click("analysis-market"); click("analytics-drawdown")
        ui.onNodeWithTag("analytics-chart-mode").assertTextEquals("Queda desde o pico anterior (%)")
        capture("visual-market-drawdown")
        click("metric-maxDrawdownPct"); ui.onNodeWithTag("metric-close").assertIsDisplayed()
        capture("visual-risk-details"); click("metric-close")
        page(Page.Settings); click("dark-theme"); page(Page.Markets); click("asset-tab-2")
        ui.onNodeWithTag("financial-chart").performScrollTo().assertIsDisplayed(); capture("visual-financial-dark")
    }
    @Test fun allNavigationAndAssetTabs() {
        for (target in listOf(Page.Portfolio, Page.Watchlist, Page.Screener, Page.Calendar, Page.Providers, Page.Settings)) {
            page(target); capture("nav-${target.name}")
        }
        back(); await("back restores providers") { state().page == Page.Providers }
        page(Page.Markets)
        for (tab in 0..4) {
            click("asset-tab-$tab"); assertEquals(tab, state().tab)
            await("auxiliary finished") { !state().auxiliaryLoading }; capture("asset-tab-$tab")
        }
        back(); await("back selects summary") { state().tab == 0 }
        click("nav-More"); back(); ui.onNodeWithTag("menu-Settings").assertDoesNotExist()
    }
    @Test fun watchlistFormsAndPersistence() {
        page(Page.Watchlist); click("add-watch"); fill("watch-input", "<bad>"); click("watch-submit")
        ui.onNodeWithTag("form-error").assertIsDisplayed(); assertEquals(6, state().watch.size)
        fill("watch-input", "AIR.PA"); click("watch-submit"); ui.onNodeWithTag("form-error").assertTextContains("já", substring = true)
        fill("watch-input", "aapl"); click("watch-submit"); await("watch added") { "AAPL" in state().watch }
        ui.onNodeWithTag("quote-AAPL").performScrollTo().performClick(); await("AAPL loaded") { state().snapshot?.text("symbol") == "AAPL" }
        restart(); assertTrue("AAPL" in state().watch)
        page(Page.Watchlist); click("remove-watch-AAPL"); assertFalse("AAPL" in state().watch)
        capture("watchlist-updated"); click("add-watch"); back(); ui.onNodeWithTag("watch-input").assertDoesNotExist()
    }
    @Test fun portfolioValidationMultiLotAndPersistence() {
        page(Page.Portfolio); click("add-position"); fill("position-qty", "-1"); fill("position-avg", "180,50"); click("position-submit")
        ui.onNodeWithTag("form-error").assertIsDisplayed(); assertTrue(state().positions.isEmpty()); capture("position-validation-error")
        fill("position-qty", "2,5"); click("position-submit"); await("first lot saved") { state().positions.size == 1 }
        click("add-position"); fill("position-qty", "1,5"); fill("position-avg", "200"); click("position-submit")
        await("second lot saved") { state().positions.size == 2 && state().quotes["AIR.PA"]?.number("price") != null }
        val total = portfolioTotals(state().positions, state().quotes).single()
        assertEquals(751.25, total.invested, .001); assertEquals(752.0, total.value!!, .001)
        ui.onNodeWithTag("total-EUR").performScrollTo(); ui.onNodeWithText("752,00 €").assertExists()
        capture("portfolio-multiple-lots"); restart(); assertEquals(2, state().positions.size)
        page(Page.Portfolio); val ids = state().positions.map { it.id }
        ids.forEach { click("remove-position-$it") }; assertTrue(state().positions.isEmpty()); capture("portfolio-empty")
    }
    @Test fun currencySeparationAndMissingQuotes() {
        page(Page.Portfolio)
        fun add(ticker: String, qty: String, avg: String, currency: String) {
            click("add-position"); fill("position-symbol", ticker); fill("position-qty", qty); fill("position-avg", avg); fill("position-currency", currency); click("position-submit")
        }
        add("AIR.PA", "1", "180", "EUR"); add("JPM", "2", "300", "USD"); add("UNKNOWN", "1", "10", "EUR")
        await("JPM quote") { state().quotes["JPM"]?.number("price") == 310.0 }
        val totals = portfolioTotals(state().positions, state().quotes).associateBy { it.currency }
        assertEquals(2, totals.size); assertNull(totals.getValue("EUR").value); assertEquals(620.0, totals.getValue("USD").value!!, .001)
        capture("portfolio-currency-and-missing")
    }
    @Test fun demoLiveClearsSyntheticDataAndPersistsRouting() {
        page(Page.Providers); click("mode-live")
        await("live without keys") { !state().demo && !state().saving && !state().loading && state().snapshot != null }
        assertNull(state().snapshot!!.number("price")); assertNull(state().snapshot!!.number("score")); assertTrue(state().history.isEmpty())
        page(Page.Markets); ui.onNodeWithTag("asset-price").assertTextEquals("—"); capture("live-without-keys")
        page(Page.Providers); click("route-quotes"); click("route-option-quotes-twelve"); click("save-routing")
        await("routing saved") { state().routing["quotes"] == "twelve" && !state().saving }
        restart(); assertFalse(state().demo); assertEquals("twelve", state().routing["quotes"])
        page(Page.Providers); click("mode-demo"); await("demo restored") { state().demo && state().snapshot?.number("price") == 188.0 }
    }
    @Test fun encryptedCredentialsAndFiveProviderReports() {
        page(Page.Providers); fill("key-eodhd", "instrumentation-fixture-only"); click("save-keys")
        await("key stored") { "eodhd" in state().configured && !state().saving }
        val stored = context.getSharedPreferences("secure_providers", 0).getString("config", "")!!
        assertFalse(stored.contains("instrumentation-fixture-only"))
        val actual = SecureSettings(context).load(); assertEquals("instrumentation-fixture-only", actual.getString("eodhdKey"))
        ui.onNodeWithTag("key-eodhd").assertTextContains("", substring = true)
        click("clear-key-eodhd"); await("key removed") { state().configured.isEmpty() && !state().saving }
        click("test-providers"); await("five missing key reports") { state().reports.size == 5 && !state().testing }
        assertTrue(state().reports.all { it.text("code") == "missing_key" && !it.optBoolean("connected") })
        ui.onNodeWithTag("report-eodhd").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("capability-eodhd-fundamentals").assertExists()
        capture("providers-test-results")
        restart(); assertTrue(state().configured.isEmpty())
    }
    @Test fun searchScreenerAndBack() {
        click("open-search"); fill("search-input", "<bad>"); click("search-submit"); ui.onNodeWithTag("form-error").assertIsDisplayed()
        fill("search-input", "JPM"); ui.onNodeWithTag("search-input").performImeAction()
        await("JPM selected") { state().snapshot?.text("symbol") == "JPM" }; assertEquals(Page.Markets, state().page)
        page(Page.Screener); click("run-screener"); await("screening completed") { !state().screeningBusy && state().screening.size == 6 }
        fill("screener-filter", "AIR.PA"); ui.onNodeWithTag("screened-AIR.PA").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("screened-JPM").assertDoesNotExist(); capture("screener-filtered")
        click("quote-AIR.PA"); await("AIR selected") { state().snapshot?.text("symbol") == "AIR.PA" }
    }
    @Test fun chartRangesAndExpansion() {
        page(Page.Markets); click("range-365"); await("one year history") { !state().historyLoading && state().days == 365 && state().history.isNotEmpty() }
        ui.onNodeWithTag("price-chart").performScrollTo().performTouchInput { click(center) }
        capture("chart-crosshair")
        ui.onNodeWithText("Velas").assertIsNotEnabled(); ui.onNodeWithText("Ampliar").performScrollTo().performClick()
        ui.onNodeWithTag("close-chart").assertIsDisplayed(); capture("chart-expanded"); back(); ui.onNodeWithTag("close-chart").assertDoesNotExist()
        click("range-30"); await("one month history") { !state().historyLoading && state().days == 30 }
    }
    private fun orient(orientation: Int) {
        scenario.onActivity { it.requestedOrientation = orientation }
        val landscape = orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        await("committed viewport orientation") {
            var matches = false
            instrumentation.runOnMainSync {
                val decor = activity.window.decorView
                val size = android.graphics.Point()
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.getRealSize(size)
                matches = decor.width > 0 && decor.height > 0 && (decor.width > decor.height) == landscape && (size.x > size.y) == landscape
            }
            matches
        }
        // Resource configuration changes before the OS rotation animation commits.
        SystemClock.sleep(1500)
        ui.waitForIdle()
        var safeLeft = 0; var safeRight = 0
        instrumentation.runOnMainSync {
            val decor = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            safeLeft = insets?.left ?: 0; safeRight = decor.width - (insets?.right ?: 0)
        }
        val refresh = ui.onNodeWithTag("refresh").fetchSemanticsNode().boundsInRoot
        assertTrue("Toolbar action must remain outside side navigation bars/cutouts", refresh.left >= safeLeft - 1 && refresh.right <= safeRight + 1)
    }
    @Test fun rotationKeyboardAndDialog() {
        page(Page.Watchlist)
        orient(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        ui.onNodeWithTag("screen-Watchlist").assertIsDisplayed(); capture("landscape-watchlist", true)
        orient(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        capture("portrait-watchlist", false)
        instrumentation.uiAutomation.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1").use { fd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() } }
        click("add-watch"); click("watch-input")
        await("keyboard visible") {
            var visible = false
            instrumentation.runOnMainSync { visible = ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }; visible
        }
        ui.onNodeWithTag("watch-submit").assertIsDisplayed(); capture("keyboard-watch-form"); back(); back()
    }
    @Test fun themeAndActivityRestoration() {
        page(Page.Settings); click("dark-theme"); assertTrue(state().dark); capture("dark-settings")
        page(Page.Markets); click("asset-tab-2"); scenario.recreate(); scenario.onActivity { activity = it }
        ui.onNodeWithTag("screen-Markets").assertIsDisplayed(); assertEquals(2, state().tab); assertTrue(state().dark); capture("dark-financials")
        page(Page.Home); back()
        await("Home back finishes after an asset secondary tab") { scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        restart(); assertTrue(state().dark)
    }
    @Test fun sqliteCacheAndTransportPolicy() {
        ApiCache(context).use { cache ->
            val url = "https://eodhd.com/api/quote?api_token=cache-fixture"
            cache.put(url, "{\"close\":123}"); assertNotNull(cache.get(url, 60)); assertNull(cache.get(url, 0))
            cache.readableDatabase.rawQuery("SELECT cache_key FROM http_cache_v2", null).use { c -> assertTrue(c.moveToFirst()); assertEquals(64, c.getString(0).length); assertFalse(c.getString(0).contains("cache-fixture")) }
            try { HttpClient(cache).get("http://eodhd.com/api/quote"); fail("Cleartext must be rejected") } catch (_: IllegalArgumentException) { }
            try { HttpClient(cache).get("https://example.org/api/quote"); fail("Unexpected host must be rejected") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun legacyWebViewMigrationPreservesData() {
        val seeded = CountDownLatch(1); lateinit var web: WebView
        instrumentation.runOnMainSync {
            web = WebView(context); web.settings.apply { javaScriptEnabled = true; domStorageEnabled = true }
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript("localStorage.setItem('ir_watch','[\"AAPL\"]');localStorage.setItem('ir_portfolio','[{\"symbol\":\"AIR.PA\",\"qty\":2.5,\"avg\":180.5,\"currency\":\"EUR\"}]');true") { seeded.countDown() }
                }
            }
            web.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/index.html", "<!doctype html><html></html>", "text/html", "UTF-8", null)
        }
        assertTrue(seeded.await(60, TimeUnit.SECONDS)); instrumentation.runOnMainSync { web.destroy() }
        val migrated = CountDownLatch(1); var result: JSONObject? = null
        instrumentation.runOnMainSync { LegacyPortfolioMigration(context, { result = it; migrated.countDown() }, { migrated.countDown() }).start() }
        assertTrue(migrated.await(60, TimeUnit.SECONDS)); assertNotNull("Actual old WebView storage must migrate", result)
        val store = LocalPortfolio(context); store.migrate(result!!)
        assertEquals(listOf("AAPL"), store.watch()); assertEquals(2.5, store.positions().single().qty, .001)
        restart(); assertEquals(listOf("AAPL"), state().watch); assertEquals(1, state().positions.size)
        page(Page.Portfolio); capture("portfolio-migrated-from-webview")
    }
    @Test fun rapidAssetChangesNeverReplaceLatestSelection() {
        search("JPM"); search("AAPL"); search("TSM")
        await("latest selection completes") { !state().loading && state().snapshot?.text("symbol") == "TSM" }
        ui.onNodeWithTag("asset-price").assertTextContains("330", substring = true); assertEquals("TSM", state().symbol)
        click("asset-tab-4"); search("AIR.PA"); await("new asset clears old news") { state().snapshot?.text("symbol") == "AIR.PA" }
        assertTrue(state().news.isEmpty()); assertEquals(0, state().tab)
    }    @Test fun quoteCacheMigrationIsolationFreshnessAndBudget() {
        val legacy = android.database.sqlite.SQLiteDatabase.create(null)
        legacy.execSQL("CREATE TABLE http_cache_v2(cache_key TEXT PRIMARY KEY,body TEXT NOT NULL,fetched_at INTEGER NOT NULL)")
        legacy.execSQL("INSERT INTO http_cache_v2 VALUES('preserved','body',1)")
        ApiCache(context).use { it.onUpgrade(legacy, 2, 3) }
        legacy.rawQuery("SELECT body FROM http_cache_v2", null).use { assertTrue(it.moveToFirst()); assertEquals("body", it.getString(0)) }
        legacy.rawQuery("SELECT count(*) FROM quote_values", null).use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        legacy.close()
        val now = java.time.Instant.now().epochSecond
        val live = JSONObject().put("demo", false).put("eodhdKey", "cache-fixture")
        val scope = quoteCacheScope(live)
        val quote = JSONObject().put("symbol", "AAPL").put("price", 150).put("currency", "USD").put("mode", "live").put("timestamp", now-86400)
        ApiCache(context).use { cache ->
            cache.saveQuote(scope, quote, now-1000)
            val stale=cache.readQuotes(scope, listOf("AAPL"), 900, now).getValue("AAPL")
            assertTrue(stale.getBoolean("cacheStale")); assertEquals(now-86400, stale.getLong("timestamp"))
            assertTrue(quoteCacheLabel(stale).startsWith("Em cache"))
            assertEquals(listOf("AAPL"), cache.dueQuotes(scope, listOf("AAPL"), 900, false, now))
            cache.attemptQuote(scope, "AAPL", now)
            assertTrue(cache.dueQuotes(scope, listOf("AAPL"), 900, true, now+29).isEmpty())
            assertEquals(listOf("AAPL"), cache.dueQuotes(scope, listOf("AAPL"), 900, true, now+30))
            assertEquals(150.0, cache.readQuotes(scope, listOf("AAPL"), 900, now).getValue("AAPL").getDouble("price"), .001)
            val demo=JSONObject(live.toString()).put("demo", true)
            val newKey=JSONObject(live.toString()).put("eodhdKey", "other-fixture")
            val route=JSONObject(live.toString()).put("routing", JSONObject().put("quotes", "fmp"))
            for (different in listOf(demo,newKey,route)) assertTrue(cache.readQuotes(quoteCacheScope(different), listOf("AAPL"),900,now).isEmpty())
            val workers=java.util.concurrent.Executors.newFixedThreadPool(4)
            try {
                val attempts=(1..60).map { workers.submit<Boolean> { cache.takeQuoteBudget(20,now) } }
                assertEquals(20, attempts.count { it.get(10, TimeUnit.SECONDS) })
            } finally { workers.shutdownNow() }
            assertTrue(cache.takeQuoteBudget(20, now+86400))
            val account=quoteCredentialScope("eodhd.com","cache-fixture")
            assertEquals(account, quoteCredentialScope(java.net.URL("https://eodhd.com/api/real-time/AAPL.US?api_token=cache-fixture&fmt=json")))
            cache.blockQuote(account,"rate_limit",now)
            assertTrue(cache.quoteBlocked(account,now+3599)); assertFalse(cache.quoteBlocked(account,now+3600))
            cache.blockQuote(account,"rate_limit",now+3600)
            assertTrue(cache.quoteBlocked(account,now+10799)); assertFalse(cache.quoteBlocked(account,now+10800))
            assertFalse(cache.quoteBlocked(quoteCredentialScope("eodhd.com","new-fixture"),now))
        }
    }
    @Test fun automaticBudgetStopsRealTransportAndRetainsCachedQuote() {
        scenario.close()
        val config=JSONObject().put("demo",false)
        providerKeys.values.forEach { config.put(it,"budget-fixture") }
        SecureSettings(context).save(config)
        QuotePollingSettings(context).dailyLimit=20
        val scope=quoteCacheScope(config); val now=java.time.Instant.now().epochSecond
        ApiCache(context).use { cache ->
            cache.saveQuote(scope,JSONObject().put("symbol","AAPL").put("price",150).put("currency","USD").put("mode","live").put("available",true),now-1000)
            repeat(20) { assertTrue(cache.takeQuoteBudget(20,now)) }
        }
        val repository=ResearchRepository(context)
        try {
            kotlinx.coroutines.runBlocking {
                val result=repository.refreshQuotes(listOf("AAPL","AAPL"))
                assertTrue(result.budgetReached)
                val quote=result.quotes.getValue("AAPL")
                assertEquals(150.0,quote.getDouble("price"),.001); assertTrue(quote.getBoolean("cacheStale"))
                assertEquals(now-1000,quote.getLong("cacheFetchedAt"))
                assertEquals(150.0,repository.cachedQuotes(listOf("AAPL")).getValue("AAPL").getDouble("price"),.001)
            }
            ApiCache(context).use { cache ->
                cache.readableDatabase.rawQuery("SELECT count(*) FROM quote_backoff",null).use { it.moveToFirst(); assertEquals("App budget must not poison provider backoff",0,it.getInt(0)) }
            }
        } finally { repository.close() }
        // Restore a usable Demo Activity for teardown; all previous values stay in the Live namespace.
        SecureSettings(context).save(JSONObject().put("demo",true)); start()
        assertEquals(188.0,state().snapshot!!.getDouble("price"),.001)
        assertTrue(state().quotes.values.none { it.text("mode")=="live" })
    }
    @Test fun cachedFavoritePriceSurvivesRefreshFailureAndRecreation() {
        liveFixture(false)
        val config=SecureSettings(context).load(); val now=java.time.Instant.now().epochSecond
        ApiCache(context).use { cache ->
            cache.saveQuote(quoteCacheScope(config),JSONObject().put("symbol","VUAA.DE").put("name","Vanguard S&P 500 UCITS ETF").put("price",140).put("currency","EUR").put("mode","live").put("available",true).put("source","EODHD").put("timestamp",now-3600),now-1000)
            cache.blockQuote(quoteCredentialScope("eodhd.com",config.text("eodhdKey")),"rate_limit",now)
        }
        restart()
        await("persisted favorite hydrated") { state().quotes["VUAA.DE"]?.number("price")==140.0 }
        ui.onNodeWithTag("quote-VUAA.DE").performScrollTo().assertTextContains("140,00",substring=true)
        capture("polling-cached-favorite-home")
        click("quote-VUAA.DE")
        await("snapshot keeps cached latest price") { !state().loading && state().snapshot?.text("symbol")=="VUAA.DE" }
        ui.onNodeWithTag("asset-price").assertTextContains("140,00",substring=true)
        ui.onNodeWithTag("asset-cache-time").assertTextContains("Em cache",substring=true)
        assertEquals("Fundamental snapshot remains independent",132.8,state().snapshot!!.getDouble("price"),.001)
        scenario.onActivity { it.model.refresh(forceQuotes=true) }
        await("manual refresh completed") { !state().loading && state().quotes["VUAA.DE"]?.optBoolean("cacheStale")==true }
        ui.onNodeWithTag("asset-price").assertTextContains("140,00",substring=true)
        capture("polling-stale-quote-retained")
        scenario.recreate(); scenario.onActivity { activity=it }
        await("cached quote after recreation") { state().ready && !state().loading && state().quotes["VUAA.DE"]?.number("price")==140.0 }
        ui.onNodeWithTag("asset-price").assertTextContains("140,00",substring=true)
        val merged=mergeQuote(JSONObject().put("mode","live").put("price",100).put("score",70).put("pe",20).put("name","Company"),JSONObject().put("mode","live").put("symbol","AAPL").put("name","AAPL").put("price",200).put("source","Finnhub"))
        assertEquals(200,merged.getInt("price")); assertEquals(70,merged.getInt("score")); assertEquals(20,merged.getInt("pe")); assertEquals("Company",merged.text("name"))
    }
    @Test fun pollingControlsLifecycleAndBackgroundScheduling() {
        page(Page.Settings)
        click("polling-interval"); click("polling-interval-5")
        click("polling-budget"); click("polling-budget-50")
        click("polling-background")
        assertTrue(state().pollingBackground); assertEquals(5,state().pollingMinutes); assertEquals(50,state().pollingDailyLimit)
        capture("polling-settings")
        page(Page.Providers); click("mode-live")
        await("foreground polling Live") { !state().saving && !state().demo && state().pollingActive }
        val manager=androidx.work.WorkManager.getInstance(context)
        val work=manager.getWorkInfosForUniqueWork("favorite-quote-polling").get(20,TimeUnit.SECONDS)
        assertEquals(1,work.count { !it.state.isFinished })
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        assertFalse(state().pollingActive)
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        await("foreground polling resumed") { state().pollingActive }
        page(Page.Settings); click("polling-enabled")
        await("polling paused") { !state().pollingActive && !state().pollingEnabled }
        manager.getWorkInfosForUniqueWork("favorite-quote-polling").get(20,TimeUnit.SECONDS).forEach { assertTrue(it.state.isFinished) }
        restart()
        assertFalse(state().pollingEnabled); assertTrue(state().pollingBackground); assertEquals(5,state().pollingMinutes); assertEquals(50,state().pollingDailyLimit)
        page(Page.Settings); capture("polling-paused-settings")
    }

}
