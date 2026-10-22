package com.investmentradar.app

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

internal data class CataloguePage(val items: List<DirectoryAsset>, val matches: Int)
/** Immutable, read-only discovery index. Call on Dispatchers.IO; never consumes API quota. */
internal object GlobalCatalogue {
    private var database: SQLiteDatabase? = null
    private var metadata: JSONObject? = null
    @Synchronized fun manifest(context: Context): JSONObject = metadata ?: context.assets.open("catalogue/manifest.json").bufferedReader().use { JSONObject(it.readText()) }.also { metadata = it }
    @Synchronized private fun open(context: Context): SQLiteDatabase {
        database?.let { return it }
        val manifest = manifest(context)
        val folder = File(context.cacheDir, "catalogue/${manifest.getString("revision")}").apply { mkdirs() }
        val file = File(folder, "assets.sqlite")
        if (!file.exists() || file.length() != manifest.getLong("databaseBytes")) {
            val temporary = File(folder, "assets.tmp")
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                context.assets.open("catalogue/assets.sqlite.gz").use { raw -> GZIPInputStream(raw).use { source ->
                    DigestOutputStream(temporary.outputStream(), digest).use { target -> source.copyTo(target) }
                } }
                check(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == manifest.getString("sha256"))
                check(temporary.length() == manifest.getLong("databaseBytes"))
                check(temporary.renameTo(file))
            } finally { temporary.delete() }
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).also { database = it }
    }
    fun search(context: Context, query: String, type: String = "Todos", sector: String = "Todos", country: String = "Todos", cap: String = "Todas", exchange: String = "Todas", limit: Int = 40): CataloguePage {
        val db = open(context)
        val clauses = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        val tokens = Regex("[\\p{L}\\p{N}]+").findAll(searchKey(query).take(120)).map { it.value }.take(12).toList()
        val from = if (tokens.isEmpty()) "assets a" else "assets a JOIN search ON search.docid=a.id"
        if (tokens.isNotEmpty()) { clauses += "search MATCH ?"; arguments += tokens.joinToString(" ") { "$it*" } }
        for ((column, value, all) in listOf(Triple("type", type, "Todos"), Triple("sector", sector, "Todos"), Triple("country", country, "Todos"), Triple("market_cap", cap, "Todas"), Triple("exchange", exchange, "Todas"))) {
            if (value != all) { clauses += "a.$column=?"; arguments += value }
        }
        val where = if (clauses.isEmpty()) "" else " WHERE " + clauses.joinToString(" AND ")
        val total = db.rawQuery("SELECT count(*) FROM $from$where", arguments.toTypedArray()).use { it.moveToFirst(); it.getInt(0) }
        val order = " ORDER BY CASE WHEN a.symbol=? OR a.isin=? THEN 0 ELSE 1 END,a.name,a.symbol LIMIT ${limit.coerceIn(1, 100)}"
        val rows = db.rawQuery("SELECT a.* FROM $from$where$order", (arguments + listOf(query.trim().uppercase(java.util.Locale.ROOT), query.trim().uppercase(java.util.Locale.ROOT))).toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(read(c)) }
        }
        return CataloguePage(rows, total)
    }
    fun lookup(context: Context, symbol: String): DirectoryAsset? = open(context).rawQuery(
        "SELECT a.*,d.text AS description FROM assets a LEFT JOIN descriptions d ON d.id=a.description_id WHERE a.symbol=?", arrayOf(symbol)
    ).use { c -> if (c.moveToFirst()) read(c, true) else null }
    private fun read(c: Cursor, description: Boolean = false): DirectoryAsset {
        fun value(key: String) = c.getString(c.getColumnIndexOrThrow(key)).orEmpty()
        val summary = if (description && !c.isNull(c.getColumnIndexOrThrow("description")))
            InflaterInputStream(c.getBlob(c.getColumnIndexOrThrow("description")).inputStream()).bufferedReader().use { it.readText() } else ""
        val asset = DirectoryAsset(value("symbol"), value("name"), value("sector"), value("currency"), value("isin"), value("type"), value("region"),
            source = "FinanceDatabase · 20/10/2026", exchange = value("exchange"), country = value("country"), industry = value("industry"),
            summary = summary, website = value("website"), marketCap = value("market_cap"), issuer = value("issuer"))
        val named = if (asset.type == "ETF") asset.copy(accumulating = when {
            Regex("(?i)\\b(acc|accumulation|accumulating)\\b").containsMatchIn(asset.name) -> true
            Regex("(?i)\\b(dist|distributing|distribution)\\b").containsMatchIn(asset.name) -> false
            else -> null
        }) else asset
        val known = directoryAsset(asset.symbol) ?: return named
        return asset.copy(name = if (known.type == "ETF") known.name else asset.name, sector = known.sector, region = known.region, accumulating = known.accumulating, aliases = known.aliases)
    }
}
