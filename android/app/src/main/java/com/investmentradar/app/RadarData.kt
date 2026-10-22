package com.investmentradar.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

internal fun validSymbol(value: String) = value.matches(Regex("[A-Z0-9.^:-]{1,32}"))
internal fun symbol(value: String) = value.trim().uppercase(Locale.ROOT)
internal fun JSONObject.number(key: String): Double? = if (isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
internal fun JSONObject.text(key: String) = if (isNull(key)) "" else optString(key)
internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
internal val defaultWatch = listOf("AIR.PA", "VUAA.DE", "6AQQ.DE", "JPM", "GS", "TSM")
internal val providerKeys = linkedMapOf("eodhd" to "eodhdKey", "fmp" to "fmpKey", "twelve" to "twelveKey", "alpha" to "alphaKey", "finnhub" to "finnhubKey")
internal val providerNames = mapOf("auto" to "Automático", "eodhd" to "EODHD", "fmp" to "Financial Modeling Prep", "twelve" to "Twelve Data", "alpha" to "Alpha Vantage", "finnhub" to "Finnhub")
internal val capabilities = linkedMapOf("quotes" to "Cotações", "history" to "Histórico", "fundamentals" to "Fundamentais", "estimates" to "Estimativas", "earnings" to "Resultados", "news" to "Notícias")
internal val providerLinks = mapOf("eodhd" to "https://eodhd.com/register", "fmp" to "https://site.financialmodelingprep.com", "twelve" to "https://twelvedata.com", "alpha" to "https://www.alphavantage.co/support/#api-key", "finnhub" to "https://finnhub.io/register")

/** This class is the JNI owner. No Activity, JavaScript or provider models enter the core. */
class NativeCore(context: Context) {
    companion object { init { System.loadLibrary("investment_radar") } }
    private val cache = ApiCache(context)
    private val http = HttpClient(cache)
    external fun nativeRequest(path: String, symbol: String, settings: String): String
    @Suppress("unused") // Called synchronously by JNI on a worker thread.
    fun fetchUrl(url: String, bypassCache: Boolean): String = try {
        JSONObject().put("body", http.get(url, bypassCache || (QuoteRequestContext.current.get() != null && ProviderPolicy.isQuoteUrl(url)))).toString()
    } catch (e: ProviderHttpException) {
        JSONObject().put("httpStatus", e.status).toString()
    } catch (_: Exception) { JSONObject().put("error", "network").toString() }
    internal fun close() = cache.close()
}

internal class ResearchRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val core = NativeCore(context)
    private val secure = SecureSettings(context)
    private val configLock = Any()
    private val workers = object : ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>()) {
        override fun terminated() { core.close(); quoteCache.close() }
    }
    private val dispatcher = workers.asCoroutineDispatcher()
    private val quoteCache = ApiCache(context)
    private val pollSettings = QuotePollingSettings(context)
    private val quoteMutex = quoteRefreshMutex
    private fun decode(raw: String): Any {
        val result = JSONObject(raw)
        check(!result.has("error")) { "Não foi possível obter os dados. Tenta novamente." }
        return result.get("data")
    }
    suspend fun request(path: String, argument: String = ""): Any = withContext(dispatcher) {
        val config = synchronized(configLock) { secure.load().toString() }
        decode(core.nativeRequest(path, if (path == "/api/snapshot") {
            val payload = if (argument.trimStart().startsWith("{")) JSONObject(argument) else JSONObject().put("symbol", argument.ifBlank { "AIR.PA" })
            val selected = payload.text("symbol")
            val type = directoryAsset(selected)?.type ?: runCatching { GlobalCatalogue.instrumentType(applicationContext, selected) }.getOrNull()
            // Transport reference metadata only; classification and scoring remain in C++.
            payload.put("referenceType", if (type == "ETF") "etf" else if (type == "Fundo") "fund" else "unknown").toString()
        } else argument, config))
    }
    suspend fun save(update: JSONObject): JSONObject = withContext(dispatcher) {
        synchronized(configLock) {
            val current = secure.load()
            update.keys().forEach { current.put(it, update.get(it)) }
            val validated = decode(core.nativeRequest("/api/validate", "", current.toString())) as JSONObject
            secure.save(validated)
            decode(core.nativeRequest("/api/settings", "", validated.toString())) as JSONObject
        }
    }
    suspend fun cachedQuotes(symbols: List<String>): Map<String, JSONObject> = withContext(dispatcher) {
        val config = synchronized(configLock) { secure.load() }
        quoteCache.readQuotes(quoteCacheScope(config), symbols, pollSettings.intervalMinutes * 60L)
    }
    suspend fun refreshQuotes(symbols: List<String>, force: Boolean = false): QuoteRefreshResult = quoteMutex.withLock {
        withContext(dispatcher) {
            val config = synchronized(configLock) { secure.load() }
            val scope = quoteCacheScope(config)
            val now = java.time.Instant.now().epochSecond
            val selected = symbols.distinct().filter(::validSymbol)
            val due = quoteCache.dueQuotes(scope, selected, pollSettings.intervalMinutes * 60L, force)
            val request = QuoteRequestContext(!force && !config.optBoolean("demo", true), pollSettings.dailyLimit)
            if (due.isNotEmpty()) {
                due.forEach { quoteCache.attemptQuote(scope, it, now) }
                QuoteRequestContext.current.set(request)
                try {
                    due.chunked(32).forEach { batch ->
                        val rows = (decode(core.nativeRequest("/api/quotes", JSONArray(batch).toString(), config.toString())) as JSONArray).objects()
                        rows.forEach { row ->
                            if (row.number("price")?.let { it > 0 } == true && row.optBoolean("available") && row.text("mode") == (if (config.optBoolean("demo", true)) "demo" else "live")) quoteCache.saveQuote(scope, row, java.time.Instant.now().epochSecond)
                            if (!request.budgetReached) row.optJSONArray("issues")?.objects()?.forEach { issue ->
                                val id = issue.text("providerId")
                                val key = providerKeys[id]?.let { config.text(it) }.orEmpty()
                                val host = quoteProviderHosts[id]
                                if (host != null && key.isNotEmpty() && issue.text("code") in listOf("rate_limit", "authentication", "subscription")) quoteCache.blockQuote(quoteCredentialScope(host, key), issue.text("code"))
                            }
                        }
                    }
                } finally { QuoteRequestContext.current.remove() }
            }
            QuoteRefreshResult(quoteCache.readQuotes(scope, selected, pollSettings.intervalMinutes * 60L), request.budgetReached)
        }
    }
    fun close() { workers.shutdown() }
}

internal data class Position(val id: String, val symbol: String, val qty: Double, val avg: Double, val currency: String) {
    fun json() = JSONObject().put("id", id).put("symbol", symbol).put("qty", qty).put("avg", avg).put("currency", currency)
}

/** Local holdings are separate from encrypted provider credentials; writes are atomic. */
internal class LocalPortfolio(context: Context) {
    private val prefs = context.getSharedPreferences("radar_native", Context.MODE_PRIVATE)
    val migrated get() = prefs.getBoolean("migrated", false)
    fun watch(): List<String> = runCatching {
        JSONArray(prefs.getString("watch", JSONArray(defaultWatch).toString())).strings().filter(::validSymbol).distinct()
    }.getOrDefault(defaultWatch)
    fun positions(): List<Position> = runCatching { decodePositions(JSONArray(prefs.getString("positions", "[]"))) }.getOrDefault(emptyList())
    fun write(watch: List<String>, positions: List<Position>, migrated: Boolean = this.migrated) {
        check(prefs.edit().putString("watch", JSONArray(watch).toString())
            .putString("positions", JSONArray(positions.map { it.json() }).toString())
            .putBoolean("migrated", migrated).commit()) { "Não foi possível guardar no dispositivo." }
    }
    fun migrate(raw: JSONObject) {
        // Missing legacy keys mean an untouched install; an intentional empty list stays empty.
        val watch = raw.optJSONArray("watch")?.strings()?.also { require(it.all(::validSymbol)) }?.distinct() ?: watch()
        val positions = raw.optJSONArray("portfolio")?.let(::decodePositions) ?: positions()
        write(watch, positions, true)
    }
    private fun decodePositions(array: JSONArray): List<Position> = array.objects().map {
        val s = it.getString("symbol"); val q = it.getDouble("qty"); val a = it.getDouble("avg"); val c = it.getString("currency")
        require(validSymbol(s) && q.isFinite() && a.isFinite() && q > 0 && a > 0 && (q * a).isFinite() && c.matches(Regex("[A-Z]{3}")))
        Position(it.optString("id").ifBlank { UUID.randomUUID().toString() }, s, q, a, c)
    }
}

internal data class CurrencyTotal(val currency: String, val invested: Double, val value: Double?)
internal fun portfolioTotals(positions: List<Position>, quotes: Map<String, JSONObject>): List<CurrencyTotal> =
    positions.groupBy { it.currency }.map { (currency, rows) ->
        val known = rows.all { quotes[it.symbol]?.let { q -> q.text("currency") == currency && q.number("price") != null } == true }
        CurrencyTotal(currency, rows.sumOf { it.qty * it.avg }, if (known) rows.sumOf { it.qty * quotes.getValue(it.symbol).number("price")!! } else null)
    }

/** Replace quote fields only: analytical results retain their original provenance. */
internal fun mergeQuote(previous: JSONObject?, quote: JSONObject): JSONObject {
    val result = JSONObject((previous?.takeIf { it.text("mode") == quote.text("mode") } ?: JSONObject()).toString())
    listOf("symbol", "price", "currency", "changePct", "timestamp", "source", "mode", "available", "cacheFetchedAt", "cacheStale").forEach { key ->
        if (quote.has(key)) result.put(key, quote.get(key))
    }
    val name = quote.text("name")
    if (name.isNotBlank() && (name != quote.text("symbol") || result.text("name").isBlank())) result.put("name", name)
    if (quote.has("source")) {
        val sources = JSONObject(result.optJSONObject("sources")?.toString() ?: "{}")
        result.put("sources", sources.put("quote", quote.get("source")))
    }
    return result
}
internal fun quoteCacheLabel(quote: JSONObject): String {
    fun time(key: String) = quote.number(key)?.takeIf { it > 0 }?.let {
        java.time.Instant.ofEpochSecond(it.toLong()).atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    }
    val stamp = time("timestamp")?.let { "Cotação $it" } ?: "Hora da cotação indisponível"
    return (if (quote.optBoolean("cacheStale")) "Em cache · " else "") + stamp +
        (time("cacheFetchedAt")?.let { " · recebida $it" } ?: "")
}
