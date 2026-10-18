package com.investmentradar.desktop.model

data class PortfolioCurrencyPerformance(
    val invested: Double,
    val marketValue: Double?,
    val unrealized: Double?,
    val returnPercent: Double?,
    val indicativeSessionChange: Double?,
    val complete: Boolean,
    val sessionChangeComplete: Boolean
)

/**
 * All amounts are in the instrument quote currency. No implied FX conversion,
 * no substituted purchase prices for missing quotes and no invented daily P/L.
 * Session change uses the provider's quoted percentage change, not trade history.
 */
fun portfolioCurrencyPerformance(
    currency: String, positions: List<PortfolioPosition>, quotes: Map<String, QuoteDto>
): PortfolioCurrencyPerformance {
    val invested = positions.sumOf { it.qty * it.avg }
    val complete = positions.isNotEmpty() && positions.all { p ->
        val q = quotes[p.symbol]
        p.currency == currency && p.qty.isFinite() && p.qty > 0 &&
            p.avg.isFinite() && p.avg > 0 &&
            q?.currency == currency && q.price?.let { it.isFinite() && it > 0 } == true
    }
    val market = if (complete) positions.sumOf { p -> p.qty * quotes.getValue(p.symbol).price!! } else null
    val gain = market?.minus(invested)
    val sessionReady = complete && positions.all { p ->
        quotes[p.symbol]?.changePct?.let { it.isFinite() && it > -100.0 } == true
    }
    val session = if (sessionReady) positions.sumOf { p ->
        val q = quotes.getValue(p.symbol)
        val change = q.changePct!!
        p.qty * q.price!! * change / (100.0 + change)
    } else null
    return PortfolioCurrencyPerformance(
        invested, market, gain,
        if (invested > 0) gain?.div(invested)?.times(100.0) else null,
        session, complete, sessionReady
    )
}
