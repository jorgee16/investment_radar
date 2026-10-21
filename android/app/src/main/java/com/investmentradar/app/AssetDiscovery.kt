package com.investmentradar.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal class AssetMetadataStore(context: Context) {
    private val prefs = context.getSharedPreferences("asset_directory_v1", 0)
    fun load(): List<DirectoryAsset> = runCatching {
        JSONArray(prefs.getString("assets", "[]")).objects().map(::directoryHit).filter { validSymbol(it.symbol) }
    }.getOrDefault(emptyList())
    fun save(assets: List<DirectoryAsset>) {
        val rows = assets.takeLast(500).map { a -> JSONObject().put("symbol", a.symbol).put("name", a.name).put("currency", a.currency)
            .put("isin", a.isin).put("exchange", a.exchange).put("country", a.region).put("sector", a.sector)
            .put("accumulating", a.accumulating ?: JSONObject.NULL).put("aliases", a.aliases)
            .put("instrumentType", if (a.type == "ETF") "etf" else if (a.type == "Ação") "stock" else "unknown").put("source", a.source) }
        check(prefs.edit().putString("assets", JSONArray(rows).toString()).commit())
    }
}
internal fun directoryHit(row: JSONObject) = DirectoryAsset(row.text("symbol"), row.text("name"), row.text("sector").ifBlank { "Sem setor" }, row.text("currency"), row.text("isin"),
    when (row.text("instrumentType")) { "etf" -> "ETF"; "stock", "equity" -> "Ação"; else -> "Desconhecido" }, row.text("country").ifBlank { "Por confirmar" }, accumulating = if (row.isNull("accumulating")) directoryAsset(row.text("symbol"))?.accumulating else row.optBoolean("accumulating"), source = row.text("source"), exchange = row.text("exchange"), aliases = row.text("aliases"))
internal fun knownAssets(state: RadarState): List<DirectoryAsset> {
    val values = (assetDirectory + state.discovered).associateBy { it.symbol }.toMutableMap()
    (state.watch + state.positions.map { it.symbol } + state.quotes.keys).distinct().forEach { symbol ->
        val quote = state.quotes[symbol]
        val old = values[symbol]
        val name = quote?.text("name")?.takeIf { it.isNotBlank() && it != symbol }
        values[symbol] = old?.copy(aliases = listOf(old.aliases, name.orEmpty()).joinToString(" "))
            ?: DirectoryAsset(symbol, name ?: symbol, "Sem setor", quote?.text("currency").orEmpty(), type = "Desconhecido", region = "Por confirmar", source = "Teus ativos")
    }
    return values.values.toList()
}
internal fun assetMetadata(state: RadarState, symbol: String): DirectoryAsset = knownAssets(state).firstOrNull { it.symbol == symbol }
    ?: DirectoryAsset(symbol, symbol, "Sem setor", "", type = "Desconhecido", region = "Por confirmar")

@Composable internal fun DiscoveryDialog(state: RadarState, model: RadarViewModel, tag: String, title: String, action: String, dismiss: () -> Unit, submit: (String) -> String?) {
    var query by rememberSaveable { mutableStateOf("") }
    var sector by rememberSaveable { mutableStateOf("Todos") }
    var type by rememberSaveable { mutableStateOf("Todos") }
    var exchange by rememberSaveable { mutableStateOf("Todas") }
    var filters by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DirectoryAsset?>(null) }
    var remote by remember { mutableStateOf(emptyList<DirectoryAsset>()) }
    var remoteQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val local = knownAssets(state)
    val candidates = ((if (remoteQuery == query) remote else emptyList()) + local).distinctBy { it.symbol }.filter {
        // Providers may match names/aliases absent from the normalized result, so their returned hits are retained.
        (remoteQuery == query && remote.any { r -> r.symbol == it.symbol } || it.matches(query)) &&
            (sector == "Todos" || it.sector == sector) && (type == "Todos" || it.type == type) && (exchange == "Todas" || it.exchange == exchange)
    }
    fun searchProviders() {
        if (loading || query.trim().length !in 2..120) return
        val request = query.trim(); remoteQuery = ""; remote = emptyList(); loading = true; searchMessage = null; focus.clearFocus()
        scope.launch {
            try {
                val data = model.discoverAssets(request)
                if (query.trim() == request) {
                    remote = data.optJSONArray("results")?.objects().orEmpty().map(::directoryHit); remoteQuery = query
                    searchMessage = data.optJSONArray("warnings")?.strings()?.joinToString("\n")?.takeIf(String::isNotBlank)
                        ?: if (remote.isEmpty()) "Sem resultados recebidos. Experimenta outro nome ou ticker." else null
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { if (query.trim() == request) searchMessage = "Pesquisa indisponível. Os resultados locais continuam disponíveis." }
            finally { loading = false }
        }
    }
    fun save() {
        val match = selected?.takeIf { it.symbol == symbol(query) } ?: candidates.singleOrNull()
            ?: local.firstOrNull { it.symbol == symbol(query) }
        val ticker = match?.symbol ?: symbol(query).takeIf { validSymbol(it) && query.trim() == it && !it.matches(Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]")) }
        if (ticker == null) { error = "Seleciona um resultado para confirmar o instrumento e a bolsa."; return }
        error = submit(ticker)
        if (error == null) {
            match?.let(model::rememberAsset)
            focus.clearFocus(); dismiss()
        }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)).padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth()) {
                    Text(title, Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
                    // Only results scroll; confirmation stays inside the visible IME-safe viewport.
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(query, { query = it.take(120); selected = null; error = null; searchMessage = null }, label = { Text("Nome completo, ticker ou ISIN") },
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {
                    if (selected != null || candidates.size == 1 || local.any { it.symbol == symbol(query) }) save()
                    else if (!state.demo && state.configured.any { it in setOf("eodhd", "alpha", "finnhub") }) searchProviders()
                    else focus.clearFocus()
                }), modifier = Modifier.fillMaxWidth().testTag("$tag-input"))
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("form-error"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Todos", "Ação", "ETF").forEach { value -> FilterChip(type == value, { type = value }, { Text(if (value == "Ação") "Ações" else value) }, Modifier.testTag("$tag-type-$value")) }
            }
            TextButton({ filters = !filters }, Modifier.testTag("$tag-filters")) { Text(if (filters) "Ocultar filtros" else "Filtrar por setor e bolsa") }
            if (filters) {
                SectorMenu(local.map { it.sector }, sector, "$tag-sector") { sector = it }
                PlanChoice("Bolsa", listOf("Todas") + (local + remote).map { it.exchange }.filter(String::isNotBlank).distinct().sorted(), exchange, "$tag-exchange") { exchange = it }
            }
            if (!state.demo) {
                OutlinedButton({ searchProviders() }, enabled = !loading && query.trim().length in 2..120 && state.configured.any { it in setOf("eodhd", "alpha", "finnhub") }, modifier = Modifier.testTag("$tag-provider-search")) { Text(if (loading) "A pesquisar…" else "Pesquisar nos providers") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            searchMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            selected?.let { Text("Selecionado: ${it.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            Text("${candidates.size} resultados · ${if (remoteQuery == query && remote.isNotEmpty()) "provider + guardados" else "catálogo e teus ativos"}", style = MaterialTheme.typography.labelMedium)
            candidates.take(30).forEach { asset ->
                Surface(Modifier.fillMaxWidth().testTag("$tag-suggestion-${asset.symbol}").clickable { selected = asset; query = asset.symbol; error = null; focus.clearFocus() },
                    shape = RoundedCornerShape(14.dp), color = if (selected?.symbol == asset.symbol) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(asset.name, style = MaterialTheme.typography.titleSmall)
                        Text(listOf(asset.symbol, asset.exchange, asset.currency, asset.type).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (asset.isin.isNotBlank()) Text(asset.isin, style = MaterialTheme.typography.labelSmall)
                        if (asset.symbol in state.watch) Text("Nos favoritos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (candidates.size > 30) Text("Mostrados 30 de ${candidates.size} resultados. Refina a pesquisa.", style = MaterialTheme.typography.labelSmall)
            if (candidates.isEmpty()) Text("Não encontraste? Altera o nome ou os filtros${if (!state.demo) ", ou pesquisa nos providers" else ""}.", style = MaterialTheme.typography.bodySmall)
            Text("Confirma a bolsa e a moeda. A pesquisa não confirma disponibilidade na XTB.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(dismiss) { Text("Cancelar") }
                        TextButton({ save() }, Modifier.testTag("$tag-submit")) { Text(action) }
                    }
                }
            }
        }
    }
}
