package com.investmentradar.app

import java.text.Normalizer

/** Offline discovery metadata only: never prices, recommendations or broker availability. */
internal data class DirectoryAsset(val symbol: String, val name: String, val sector: String, val currency: String, val isin: String = "", val type: String = "Ação", val region: String = "EUA", val accumulating: Boolean? = null, val source: String = "Catálogo local", val exchange: String = "", val aliases: String = "")
internal val assetDirectory = listOf(
    DirectoryAsset("VWCE.DE", "Vanguard FTSE All-World UCITS ETF · Acumulação", "ETF diversificados", "EUR", "IE00BK5BQT80", "ETF", "Mundial", true, "Vanguard · FTSE All-World"),
    DirectoryAsset("VUAA.DE", "Vanguard S&P 500 UCITS ETF · Acumulação", "ETF diversificados", "EUR", "IE00BFMXXD54", "ETF", "EUA", true, "Vanguard · S&P 500"),
    DirectoryAsset("EUNK.DE", "iShares Core MSCI Europe UCITS ETF · Acumulação", "ETF diversificados", "EUR", "IE00B4K48X80", "ETF", "Europa", true, "iShares · MSCI Europe"),
    DirectoryAsset("AIR.PA", "Airbus SE", "Indústria", "EUR", region = "Europa"),
    DirectoryAsset("SIE.DE", "Siemens AG", "Indústria", "EUR", region = "Europa"),
    DirectoryAsset("ASML.AS", "ASML", "Tecnologia", "EUR", region = "Europa"),
    DirectoryAsset("TSLA", "Tesla Inc.", "Consumo discricionário", "USD"),
    DirectoryAsset("KO", "The Coca-Cola Company", "Consumo essencial", "USD"),
    DirectoryAsset("AAPL", "Apple Inc.", "Tecnologia", "USD"),
    DirectoryAsset("MSFT", "Microsoft Corporation", "Tecnologia", "USD"),
    DirectoryAsset("NVDA", "NVIDIA Corporation", "Tecnologia", "USD"),
    DirectoryAsset("TSM", "Taiwan Semiconductor · ADR", "Tecnologia", "USD", region = "Ásia"),
    DirectoryAsset("GOOGL", "Alphabet", "Comunicações", "USD"),
    DirectoryAsset("AMZN", "Amazon", "Consumo discricionário", "USD"),
    DirectoryAsset("JPM", "JPMorgan Chase", "Finanças", "USD"),
    DirectoryAsset("GS", "Goldman Sachs", "Finanças", "USD"),
    DirectoryAsset("V", "Visa", "Finanças", "USD"),
    DirectoryAsset("JNJ", "Johnson & Johnson", "Saúde", "USD"),
    DirectoryAsset("PG", "Procter & Gamble", "Consumo essencial", "USD"),
    DirectoryAsset("XOM", "Exxon Mobil", "Energia", "USD")
)
internal fun searchKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT).trim()
internal fun DirectoryAsset.matches(query: String, selectedSector: String = "Todos"): Boolean =
    (selectedSector == "Todos" || sector == selectedSector) &&
        searchKey(query).split(Regex("\\s+")).all { word -> searchKey("$symbol $name $sector $isin $aliases $exchange").contains(word) }
internal fun directoryAsset(symbol: String) = assetDirectory.firstOrNull { it.symbol == symbol }
