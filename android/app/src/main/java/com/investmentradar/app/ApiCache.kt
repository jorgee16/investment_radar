package com.investmentradar.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.time.Instant

/** Persistent cache keyed by a hash so API tokens never appear in the database. */
class ApiCache(ctx: Context) : SQLiteOpenHelper(ctx, "investment_radar_cache.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE http_cache_v2(cache_key TEXT PRIMARY KEY, body TEXT NOT NULL, fetched_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("PRAGMA secure_delete=ON")
        db.execSQL("DROP TABLE IF EXISTS http_cache")
        onCreate(db)
    }
    private fun key(url: String) = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun get(url: String, ttl: Int): String? {
        readableDatabase.rawQuery("SELECT body,fetched_at FROM http_cache_v2 WHERE cache_key=?", arrayOf(key(url))).use { c ->
            if (c.moveToFirst() && Instant.now().epochSecond - c.getLong(1) in 0 until ttl.toLong())
                return c.getString(0)
        }
        return null
    }
    fun put(url: String, body: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO http_cache_v2 VALUES(?,?,?)",
            arrayOf(key(url), body, Instant.now().epochSecond))
    }
}
