package com.investmentradar.desktop.data

import com.investmentradar.desktop.model.PortfolioPosition
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.prefs.Preferences

class LocalStateStore(
    private val prefs: Preferences = Preferences.userRoot().node("com/investmentradar/desktop"),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun loadWatchlist(): List<String> =
        prefs.get("watchlist", null)?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
        }?.filter { it.matches(Regex("[A-Z0-9.^:-]{1,32}")) }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf("AIR.PA","VUAA.DE","6AQQ.DE","JPM","GS","TSM")

    fun saveWatchlist(symbols: List<String>) {
        prefs.put("watchlist", json.encodeToString(symbols.distinct()))
    }

    fun loadPortfolio(): List<PortfolioPosition> =
        prefs.get("portfolio", null)?.let {
            runCatching { json.decodeFromString<List<PortfolioPosition>>(it) }.getOrNull()
        }?.filter { it.qty > 0 && it.avg > 0 && it.currency.matches(Regex("[A-Z]{3}")) }
            ?: emptyList()

    fun savePortfolio(rows: List<PortfolioPosition>) {
        prefs.put("portfolio", json.encodeToString(rows))
    }
}
