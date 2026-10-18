package com.investmentradar.app

import java.net.URL
import java.util.Locale

/**
 * Pure platform policy shared by Android transport and WebView navigation.
 * Deliberately has no Android dependencies so it can be unit-tested on the JVM.
 */
internal object ProviderPolicy {
    private val providerHosts = setOf(
        "eodhd.com", "financialmodelingprep.com", "api.twelvedata.com",
        "www.alphavantage.co", "finnhub.io"
    )

    fun allowsProviderUrl(url: URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) &&
            url.host.lowercase(Locale.ROOT) in providerHosts &&
            url.userInfo == null &&
            (url.port == -1 || url.port == 443)

    fun ttlSeconds(url: String): Int {
        val u = url.lowercase(Locale.ROOT)
        return when {
            listOf("real-time", "/quote", "global_quote").any(u::contains) -> 900
            listOf("/api/search/", "symbol_search", "profile2").any(u::contains) -> 604800
            listOf("fundamentals", "statement", "ratios", "key-metrics",
                "analyst-estimates", "income_statement", "balance_sheet",
                "cash_flow", "stock/metric").any(u::contains) -> 43200
            u.contains("news") -> 900
            u.contains("calendar") || u.contains("earnings") -> 1800
            else -> 3600
        }
    }

    /** Never allow a privileged WebView to follow an untrusted navigation. */
    fun mayOpenExternal(
        scheme: String?, host: String?, isMainFrame: Boolean, hasGesture: Boolean
    ): Boolean =
        isMainFrame && hasGesture &&
            scheme.equals("https", ignoreCase = true) &&
            !host.isNullOrBlank() &&
            !host.equals("appassets.androidplatform.net", ignoreCase = true)
}
