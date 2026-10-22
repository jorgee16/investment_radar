package com.investmentradar.app

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class CatalogueLoad(val page: CataloguePage = CataloguePage(emptyList(), 0), val loading: Boolean = true, val error: String? = null)
/** Changing a filter cancels its predecessor; stale hits are never shown for a new query. */
@Composable internal fun rememberCataloguePage(query: String, type: String = "Todos", sector: String = "Todos", country: String = "Todos", cap: String = "Todas", exchange: String = "Todas"): CatalogueLoad {
    val context = LocalContext.current.applicationContext
    val key = listOf(query, type, sector, country, cap, exchange)
    var state by remember(key) { mutableStateOf(CatalogueLoad()) }
    LaunchedEffect(key) {
        delay(200)
        try {
            val page = withContext(Dispatchers.IO) { GlobalCatalogue.search(context, query, type, sector, country, cap, exchange) }
            state = CatalogueLoad(page, loading = false)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { state = CatalogueLoad(loading = false, error = "Catálogo global indisponível. Os teus ativos continuam acessíveis.") }
    }
    return state
}

@Composable internal fun rememberAssetProfile(state: RadarState): DirectoryAsset {
    val context = LocalContext.current.applicationContext
    val fallback = assetMetadata(state, state.symbol)
    var profile by remember(state.symbol) { mutableStateOf<DirectoryAsset?>(null) }
    LaunchedEffect(state.symbol) {
        try { profile = withContext(Dispatchers.IO) { GlobalCatalogue.lookup(context, state.symbol) } }
        catch (e: CancellationException) { throw e } catch (_: Exception) { /* Keep saved/provider metadata available. */ }
    }
    val metadata = profile ?: fallback
    val snapshot = state.snapshot
    val provider = snapshot?.optJSONObject("profile")
    return metadata.copy(
        name = assetDisplayName(state.symbol, snapshot?.text("name").orEmpty(), metadata),
        type = if (metadata.type == "Desconhecido") when (snapshot?.text("instrumentType")) { "etf" -> "ETF"; "stock", "equity" -> "Ação"; "fund" -> "Fundo"; else -> metadata.type } else metadata.type,
        country = metadata.country.ifBlank { provider?.text("country").orEmpty() },
        industry = metadata.industry.ifBlank { provider?.text("industry").orEmpty() },
        website = metadata.website.ifBlank { provider?.text("website").orEmpty() })
}
