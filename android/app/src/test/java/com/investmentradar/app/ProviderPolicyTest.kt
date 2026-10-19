package com.investmentradar.app

import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class ProviderPolicyTest {
    @Test fun pollingOnlyRecognizesQuoteEndpoints() {
        listOf("https://eodhd.com/api/real-time/AAPL.US?api_token=fixture", "https://financialmodelingprep.com/stable/quote?symbol=AAPL", "https://api.twelvedata.com/quote?symbol=AAPL", "https://finnhub.io/api/v1/quote?symbol=AAPL", "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=AAPL").forEach { assertTrue(it, ProviderPolicy.isQuoteUrl(it)) }
        listOf("https://eodhd.com/api/eod/AAPL.US?fmt=json", "https://financialmodelingprep.com/stable/profile?symbol=AAPL", "https://www.alphavantage.co/query?function=OVERVIEW").forEach { assertFalse(it, ProviderPolicy.isQuoteUrl(it)) }
    }
    @Test fun allFiveProvidersUseHttpsAndExactHosts() {
        val allowed = listOf(
            "https://eodhd.com/api/real-time/AIR.PA",
            "https://financialmodelingprep.com/stable/quote?symbol=AAPL",
            "https://api.twelvedata.com/quote?symbol=AAPL",
            "https://www.alphavantage.co/query?function=GLOBAL_QUOTE",
            "https://finnhub.io/api/v1/quote?symbol=AAPL"
        )
        allowed.forEach { assertTrue(it, ProviderPolicy.allowsProviderUrl(URL(it))) }
        listOf(
            "http://eodhd.com/api/real-time/AIR.PA",
            "https://eodhd.com.evil.example/quote",
            "https://user:pass@eodhd.com/quote",
            "https://eodhd.com:444/quote",
            "https://api.twelvedata.com.evil.example/quote"
        ).forEach { assertFalse(it, ProviderPolicy.allowsProviderUrl(URL(it))) }
    }

    @Test fun quotaSafeCacheTtlsCoverAllProviders() {
        assertEquals(900, ProviderPolicy.ttlSeconds("https://eodhd.com/api/real-time/AIR.PA"))
        assertEquals(900, ProviderPolicy.ttlSeconds("https://www.alphavantage.co/query?function=GLOBAL_QUOTE"))
        assertEquals(900, ProviderPolicy.ttlSeconds("https://finnhub.io/api/v1/quote"))
        assertEquals(43200, ProviderPolicy.ttlSeconds("https://eodhd.com/api/fundamentals/AIR.PA"))
        assertEquals(43200, ProviderPolicy.ttlSeconds("https://www.alphavantage.co/query?function=INCOME_STATEMENT"))
        assertEquals(43200, ProviderPolicy.ttlSeconds("https://www.alphavantage.co/query?function=OVERVIEW"))
        assertEquals(604800, ProviderPolicy.ttlSeconds("https://www.alphavantage.co/query?function=SYMBOL_SEARCH"))
        assertEquals(900, ProviderPolicy.ttlSeconds("https://eodhd.com/api/news"))
    }

    @Test fun onlyGestureInitiatedMainFrameHttpsArticlesLeaveTheApp() {
        assertTrue(ProviderPolicy.mayOpenExternal("https", "example.org", true, true))
        assertFalse(ProviderPolicy.mayOpenExternal("http", "example.org", true, true))
        assertFalse(ProviderPolicy.mayOpenExternal("javascript", "example.org", true, true))
        assertFalse(ProviderPolicy.mayOpenExternal("https", "example.org", false, true))
        assertFalse(ProviderPolicy.mayOpenExternal("https", "example.org", true, false))
        assertFalse(ProviderPolicy.mayOpenExternal("https", "appassets.androidplatform.net", true, true))
        assertFalse(ProviderPolicy.mayOpenExternal("https", null, true, true))
    }
}
