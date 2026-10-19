package com.investmentradar.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.work.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

internal val quoteRefreshMutex = Mutex()
internal val quoteProviderHosts = mapOf("eodhd" to "eodhd.com", "fmp" to "financialmodelingprep.com", "twelve" to "api.twelvedata.com", "alpha" to "www.alphavantage.co", "finnhub" to "finnhub.io")
private fun quoteHash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun quoteCredentialScope(host: String, key: String) = quoteHash(JSONArray(listOf(host, key)).toString())
internal fun quoteCredentialScope(url: URL): String {
    val token = url.query.orEmpty().split('&').map { it.split('=', limit = 2) }.firstOrNull { it.size == 2 && it[0] in listOf("api_token", "apikey", "token") }?.get(1).orEmpty()
    return quoteCredentialScope(url.host, URLDecoder.decode(token, "UTF-8"))
}
internal fun quoteCacheScope(config: JSONObject) = quoteHash(JSONArray().put(config.optBoolean("demo", true)).apply {
    providerKeys.values.forEach { put(config.text(it)) }
    put(config.optJSONObject("routing")?.optString("quotes", "auto") ?: "auto")
}.toString())

internal class QuoteRequestContext(val automatic: Boolean, val dailyLimit: Int) {
    var budgetReached = false
    companion object { val current = ThreadLocal<QuoteRequestContext>() }
}
internal data class QuoteRefreshResult(val quotes: Map<String, JSONObject>, val budgetReached: Boolean)

internal class QuotePollingSettings(context: Context) {
    private val prefs = context.getSharedPreferences("quote_polling", Context.MODE_PRIVATE)
    var enabled: Boolean get() = prefs.getBoolean("enabled", true); set(v) { prefs.edit().putBoolean("enabled", v).apply() }
    var background: Boolean get() = prefs.getBoolean("background", false); set(v) { prefs.edit().putBoolean("background", v).apply() }
    var intervalMinutes: Int get() = prefs.getInt("interval", 15).takeIf { it in listOf(5,15,30,60) } ?: 15; set(v) { require(v in listOf(5,15,30,60)); prefs.edit().putInt("interval", v).apply() }
    var dailyLimit: Int get() = prefs.getInt("daily", 240).coerceIn(20, 1000); set(v) { require(v in 20..1000); prefs.edit().putInt("daily", v).apply() }
}

internal fun createQuoteTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS quote_values(scope TEXT NOT NULL,symbol TEXT NOT NULL,body TEXT,fetched_at INTEGER NOT NULL DEFAULT 0,attempt_at INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(scope,symbol))")
    db.execSQL("CREATE TABLE IF NOT EXISTS quote_backoff(scope TEXT PRIMARY KEY,until_at INTEGER NOT NULL,failures INTEGER NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS quote_budget(day TEXT PRIMARY KEY,requests INTEGER NOT NULL)")
}
internal fun ApiCache.readQuotes(scope: String, symbols: List<String>, ttl: Long, now: Long = Instant.now().epochSecond): Map<String, JSONObject> = buildMap {
    symbols.forEach { symbol ->
        readableDatabase.rawQuery("SELECT body,fetched_at FROM quote_values WHERE scope=? AND symbol=?", arrayOf(scope, symbol)).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) runCatching { JSONObject(c.getString(0)).put("cacheFetchedAt", c.getLong(1)).put("cacheStale", now - c.getLong(1) !in 0 until ttl) }.getOrNull()?.let { put(symbol, it) }
        }
    }
}
internal fun ApiCache.dueQuotes(scope: String, symbols: List<String>, ttl: Long, force: Boolean, now: Long = Instant.now().epochSecond): List<String> = symbols.map { symbol ->
    var fetched = 0L; var attempt = 0L
    readableDatabase.rawQuery("SELECT fetched_at,attempt_at FROM quote_values WHERE scope=? AND symbol=?", arrayOf(scope, symbol)).use { c -> if (c.moveToFirst()) { fetched=c.getLong(0); attempt=c.getLong(1) } }
    Triple(symbol, fetched, attempt)
}.filter { (_, fetched, attempted) -> (attempted == 0L || now < attempted || now - attempted >= if (force) 30 else 60) && (force || fetched == 0L || now - fetched !in 0 until ttl) }
    .sortedWith(compareBy<Triple<String,Long,Long>> { it.third }.thenBy { it.second }).map { it.first }

internal fun ApiCache.attemptQuote(scope: String, symbol: String, now: Long) {
    writableDatabase.execSQL("INSERT OR IGNORE INTO quote_values(scope,symbol) VALUES(?,?)", arrayOf(scope, symbol))
    writableDatabase.execSQL("UPDATE quote_values SET attempt_at=? WHERE scope=? AND symbol=?", arrayOf(now, scope, symbol))
}
internal fun ApiCache.saveQuote(scope: String, quote: JSONObject, now: Long) {
    attemptQuote(scope, quote.text("symbol"), now)
    writableDatabase.execSQL("UPDATE quote_values SET body=?,fetched_at=? WHERE scope=? AND symbol=?", arrayOf(quote.toString(), now, scope, quote.text("symbol")))
}
internal fun ApiCache.quoteBlocked(scope: String, now: Long = Instant.now().epochSecond): Boolean = readableDatabase.rawQuery("SELECT until_at FROM quote_backoff WHERE scope=?", arrayOf(scope)).use { it.moveToFirst() && it.getLong(0) > now }
internal fun ApiCache.blockQuote(scope: String, code: String, now: Long = Instant.now().epochSecond) {
    val db=writableDatabase
    db.beginTransaction()
    try {
        var count=0; var until=0L
        db.rawQuery("SELECT until_at,failures FROM quote_backoff WHERE scope=?", arrayOf(scope)).use { c -> if(c.moveToFirst()) { until=c.getLong(0); count=c.getInt(1) } }
        if (until <= now) {
            val seconds = if(code == "authentication") 300L else if(code == "subscription") 21600L else (3600L * (1L shl count.coerceAtMost(5))).coerceAtMost(86400L)
            db.execSQL("INSERT OR REPLACE INTO quote_backoff VALUES(?,?,?)", arrayOf(scope, now+seconds, count+1))
        }
        db.setTransactionSuccessful()
    } finally { db.endTransaction() }
}
internal fun ApiCache.takeQuoteBudget(limit: Int, now: Long = Instant.now().epochSecond): Boolean {
    val day=Instant.ofEpochSecond(now).atZone(ZoneOffset.UTC).toLocalDate().toString()
    val db=writableDatabase; db.beginTransaction()
    try {
        db.execSQL("DELETE FROM quote_budget WHERE day<>?", arrayOf(day))
        db.execSQL("INSERT OR IGNORE INTO quote_budget VALUES(?,0)", arrayOf(day))
        val count=db.rawQuery("SELECT requests FROM quote_budget WHERE day=?", arrayOf(day)).use { it.moveToFirst(); it.getInt(0) }
        if(count >= limit) { db.setTransactionSuccessful(); return false }
        db.execSQL("UPDATE quote_budget SET requests=requests+1 WHERE day=?", arrayOf(day)); db.setTransactionSuccessful(); return true
    } finally { db.endTransaction() }
}

class QuotePollingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings=QuotePollingSettings(applicationContext)
        if (!settings.enabled || !settings.background) return Result.success()
        val repository=ResearchRepository(applicationContext)
        return try {
            val status=repository.request("/api/settings") as JSONObject
            if (!status.optBoolean("demo", true)) {
                val local=LocalPortfolio(applicationContext)
                repository.refreshQuotes(local.watch() + local.positions().map { it.symbol })
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }
        finally { repository.close() }
    }
    companion object {
        fun schedule(context: Context, live: Boolean) {
            val settings=QuotePollingSettings(context)
            val manager=WorkManager.getInstance(context)
            if (!live || !settings.enabled || !settings.background) { manager.cancelUniqueWork("favorite-quote-polling"); return }
            val work=PeriodicWorkRequestBuilder<QuotePollingWorker>(settings.intervalMinutes.coerceAtLeast(60).toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            manager.enqueueUniquePeriodicWork("favorite-quote-polling", ExistingPeriodicWorkPolicy.UPDATE, work)
        }
    }
}
