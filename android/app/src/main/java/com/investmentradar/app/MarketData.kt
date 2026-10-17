package com.investmentradar.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Platform transport/storage only. Provider normalization and routing live in C++. */
class ApiCache(ctx: Context) : SQLiteOpenHelper(ctx, "investment_radar_cache.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE http_cache_v2(cache_key TEXT PRIMARY KEY, body TEXT NOT NULL, fetched_at INTEGER NOT NULL)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("PRAGMA secure_delete=ON")
        db.execSQL("DROP TABLE IF EXISTS http_cache")
        onCreate(db)
    }
    private fun key(url: String) = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun get(url: String, ttl: Int): String? {
        readableDatabase.rawQuery("SELECT body,fetched_at FROM http_cache_v2 WHERE cache_key=?", arrayOf(key(url))).use { c ->
            if (c.moveToFirst() && Instant.now().epochSecond - c.getLong(1) in 0 until ttl.toLong()) return c.getString(0)
        }
        return null
    }
    fun put(url: String, body: String) { writableDatabase.execSQL("INSERT OR REPLACE INTO http_cache_v2 VALUES(?,?,?)", arrayOf(key(url), body, Instant.now().epochSecond)) }
}
class ProviderHttpException(val status: Int) : Exception("Provider HTTP error")
class HttpClient(private val cache: ApiCache) {
    // Keep Android and desktop quota-safe cache TTLs in sync.
    private fun ttl(u: String) = when {
        listOf("real-time", "/quote", "GLOBAL_QUOTE").any { u.contains(it) } -> 900
        listOf("/api/search/", "SYMBOL_SEARCH", "profile2").any { u.contains(it) } -> 604800
        listOf("fundamentals", "statement", "ratios", "key-metrics",
            "analyst-estimates", "INCOME_STATEMENT", "BALANCE_SHEET",
            "CASH_FLOW", "stock/metric").any { u.contains(it) } -> 43200
        u.contains("news") -> 900
        u.contains("calendar") || u.contains("earnings") -> 1800
        else -> 3600
    }
    fun get(url: String, bypassCache: Boolean = false): String {
        val parsed = URL(url)
        require(parsed.protocol == "https" && parsed.host in setOf("eodhd.com", "financialmodelingprep.com", "api.twelvedata.com", "www.alphavantage.co", "finnhub.io") && parsed.userInfo == null && (parsed.port == -1 || parsed.port == 443)) { "Unsupported provider URL" }
        if (!bypassCache) cache.get(url, ttl(url))?.let { return it }
        val connection = (parsed.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 20000; instanceFollowRedirects = false
            requestMethod = "GET"; setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode != 200) throw ProviderHttpException(connection.responseCode)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            // Cache only valid successful payloads. Never log credential-bearing URLs.
            val value = if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
            val rejected = value is JSONObject && (value.has("error") || value.has("Error Message") || value.has("Note") || value.has("Information") || value.optString("status") == "error")
            // C++ classifies provider errors. Error payloads must never enter the cache.
            if (!rejected) cache.put(url, body)
            return body
        } finally { connection.disconnect() }
    }
}
class SecureSettings(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("secure_providers", Context.MODE_PRIVATE)
    private val alias = "investment-radar-providers-v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    @Synchronized fun load(): JSONObject {
        val encoded = prefs.getString("config", null)
        if (encoded != null) {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8))
        }
        val old = ctx.getSharedPreferences("providers", Context.MODE_PRIVATE)
        val migrated = JSONObject().put("demo", true).put("routing", JSONObject())
        mapOf("eod" to "eodhdKey", "fmp" to "fmpKey", "twelve" to "twelveKey").forEach { (from,to) -> migrated.put(to, old.getString(from, "") ?: "") }
        if (old.all.isNotEmpty()) { save(migrated); check(old.edit().clear().commit()) { "Cannot clear legacy settings" } }
        return migrated
    }
    @Synchronized fun save(config: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(config.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("config", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { "Cannot save encrypted settings" }
    }
}
