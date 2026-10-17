package com.investmentradar.desktop.model

import org.junit.Assert.*
import org.junit.Test

class PortfolioPerformanceTest {
    @Test fun positiveAndNegativePositionsAggregateInOneCurrency() {
        val rows = listOf(PortfolioPosition("AIR.PA", 2.0, 100.0, "EUR"),
            PortfolioPosition("VUAA.DE", 1.0, 200.0, "EUR"))
        val quotes = mapOf("AIR.PA" to QuoteDto(price = 110.0, currency = "EUR", changePct = 10.0),
            "VUAA.DE" to QuoteDto(price = 190.0, currency = "EUR", changePct = -5.0))
        val result = portfolioCurrencyPerformance("EUR", rows, quotes)
        assertEquals(400.0, result.invested, 0.0001)
        assertEquals(410.0, result.marketValue!!, 0.0001)
        assertEquals(10.0, result.unrealized!!, 0.0001)
        assertEquals(2.5, result.returnPercent!!, 0.0001)
        assertEquals(10.0, result.indicativeSessionChange!!, 0.0001)
    }

    @Test fun missingOrMismatchedQuoteWithholdsTotalsAndSessionEstimate() {
        val rows = listOf(PortfolioPosition("AIR.PA", 1.0, 100.0, "EUR"),
            PortfolioPosition("AAPL", 1.0, 200.0, "EUR"))
        val quotes = mapOf("AIR.PA" to QuoteDto(price = 110.0, currency = "EUR", changePct = 1.0),
            "AAPL" to QuoteDto(price = 220.0, currency = "USD", changePct = 1.0))
        val result = portfolioCurrencyPerformance("EUR", rows, quotes)
        assertNull(result.marketValue)
        assertNull(result.unrealized)
        assertNull(result.indicativeSessionChange)
        assertFalse(result.complete)
    }

    @Test fun missingPercentageWithholdsSessionEstimateWithoutHidingMarketValue() {
        val rows = listOf(PortfolioPosition("AIR.PA", 1.0, 100.0, "EUR"))
        val result = portfolioCurrencyPerformance("EUR", rows,
            mapOf("AIR.PA" to QuoteDto(price = 110.0, currency = "EUR")))
        assertEquals(110.0, result.marketValue!!, 0.0001)
        assertNull(result.indicativeSessionChange)
        assertFalse(result.sessionChangeComplete)
    }
}
