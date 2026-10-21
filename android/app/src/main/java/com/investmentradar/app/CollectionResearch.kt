package com.investmentradar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable internal fun CollectionSearch(value: String, change: (String) -> Unit, tag: String, label: String = "Nome, ticker ou ISIN") {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, change, Modifier.fillMaxWidth().testTag(tag), singleLine = true, label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (value.isNotEmpty()) IconButton({ change("") }) { Icon(Icons.Default.Close, "Limpar pesquisa") } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }), shape = RoundedCornerShape(16.dp))
}
@Composable internal fun CollectionChips(options: List<String>, selected: String, tag: String, change: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option -> FilterChip(selected == option, { change(option) }, { Text(option) }, Modifier.testTag("$tag-$option")) }
    }
}
@Composable internal fun FinancialStat(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
    }
}

@Composable internal fun WatchlistScreen(state: RadarState, model: RadarViewModel, add: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Todos") }
    var sector by rememberSaveable { mutableStateOf("Todos") }
    var sorting by rememberSaveable { mutableStateOf("A minha ordem") }
    var removed by rememberSaveable { mutableStateOf<String?>(null) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val directory = knownAssets(state).associateBy { it.symbol }
    val visible = state.watch.filter { symbol -> directory[symbol]?.let { it.matches(query, sector) && (type == "Todos" || it.type == type) } == true }.let { list ->
        when (sorting) {
            "Nome" -> list.sortedBy { directory[it]?.name }
            "Maiores subidas" -> list.sortedByDescending { state.quotes[it]?.number("changePct") ?: Double.NEGATIVE_INFINITY }
            "Maiores descidas" -> list.sortedBy { state.quotes[it]?.number("changePct") ?: Double.POSITIVE_INFINITY }
            else -> list
        }
    }
    ModeBanner(state, model)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${state.watch.size} favoritos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        FilledTonalButton(add, Modifier.testTag("add-watch")) { Text("+ Descobrir") }
    }
    CollectionSearch(query, { query = it }, "watchlist-search")
    CollectionChips(listOf("Todos", "Ação", "ETF", "Desconhecido"), type, "watchlist-type") { type = it }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${visible.size} de ${state.watch.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        TextButton({ filtersOpen = !filtersOpen }, Modifier.testTag("watchlist-filters")) { Text(if (filtersOpen) "Ocultar filtros" else "Filtros e ordem") }
        if (query.isNotBlank() || type != "Todos" || sector != "Todos") TextButton({ query = ""; type = "Todos"; sector = "Todos" }, Modifier.testTag("watchlist-clear")) { Text("Limpar") }
    }
    if (filtersOpen) FinanceCard {
        SectorMenu(state.watch.mapNotNull { directory[it]?.sector }, sector, "watchlist-sector") { sector = it }
        PlanChoice("Ordenar", listOf("A minha ordem", "Nome", "Maiores subidas", "Maiores descidas"), sorting, "watchlist-sort") { sorting = it }
    }
    removed?.let { ticker -> Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$ticker removido", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton({ model.addWatch(ticker); removed = null }, Modifier.testTag("watchlist-undo")) { Text("Anular") }
        }
    } }
    if (visible.isEmpty()) FinanceCard { EmptyState(if (state.watch.isEmpty()) "Cria a tua primeira lista" else "Nenhum favorito encontrado", "Pesquisa pelo nome completo, ticker ou ISIN, ou descobre novos instrumentos.") }
    visible.forEach { ticker ->
        val asset = directory.getValue(ticker); val quote = state.quotes[ticker]
        FinanceCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).testTag("quote-$ticker").clickable { model.selectAsset(ticker) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(asset.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(ticker, asset.exchange, asset.currency).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ model.removeWatch(ticker); removed = ticker }, Modifier.testTag("remove-watch-$ticker")) { Icon(Icons.Default.Close, "Remover $ticker dos favoritos") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(money(quote?.number("price"), quote?.text("currency").orEmpty()), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                ChangeBadge(quote?.number("changePct"))
            }
            Text(listOf(asset.type, asset.sector, asset.isin).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (quote?.number("cacheFetchedAt") != null && !state.demo) Text(quoteCacheLabel(quote), style = MaterialTheme.typography.labelSmall)
            else if (quote == null || quote.number("price") == null) Text("Cotação indisponível", style = MaterialTheme.typography.labelSmall)
            else Text(if (state.demo) "DEMO · cotação sintética" else quote.text("source").ifBlank { "Fonte por confirmar" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun ScreenerScore(snapshot: JSONObject) {
    val value = snapshot.number("score"); val color = MaterialTheme.colorScheme.primary; val track = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            drawArc(track, -90f, 360f, false, style = Stroke(4.dp.toPx()))
            if (value != null) drawArc(color, -90f, (value * 3.6).toFloat().coerceIn(0f, 360f), false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }
        Text(value?.let { decimal(it, 0) } ?: "—", style = MaterialTheme.typography.labelLarge)
    }
}
@Composable internal fun ScreenerScreen(state: RadarState, model: RadarViewModel) {
    var query by rememberSaveable { mutableStateOf("") }; var minimum by rememberSaveable { mutableFloatStateOf(0f) }
    var sector by rememberSaveable { mutableStateOf("Todos") }; var type by rememberSaveable { mutableStateOf("Todos") }
    var universe by rememberSaveable { mutableStateOf("Favoritos") }; var preset by rememberSaveable { mutableStateOf("Todos") }
    var sorting by rememberSaveable { mutableStateOf("Score") }; var showFilters by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable(universe) { mutableStateOf(emptyList<String>()) }; var compare by rememberSaveable { mutableStateOf(false) }
    val symbols = if (universe == "Favoritos") state.watch else state.positions.map { it.symbol }.distinct()
    val directory = knownAssets(state).map { asset ->
        val sectorName = state.screening[asset.symbol]?.text("sector").orEmpty()
        if (asset.sector == "Sem setor" && sectorName.isNotBlank()) asset.copy(sector = sectorName) else asset
    }.associateBy { it.symbol }
    val results = state.screening.values.filter { s ->
        val asset = directory[s.text("symbol")] ?: DirectoryAsset(s.text("symbol"), s.text("name"), s.text("sector"), s.text("currency"))
        s.text("symbol") in symbols && asset.copy(aliases = asset.aliases + " " + s.text("name")).matches(query, sector) && (type == "Todos" || asset.type == type) &&
            (minimum == 0f || (s.number("score") ?: -1.0) >= minimum) && when (preset) {
                "Receita e EPS ↑" -> (s.number("revenueGrowth") ?: Double.NEGATIVE_INFINITY) > 0 && (s.number("epsGrowth") ?: Double.NEGATIVE_INFINITY) > 0
                "P/E positivo" -> (s.number("pe") ?: -1.0) > 0
                "Cobertura ≥75%" -> (s.number("coverage") ?: -1.0) >= 75
                else -> true
            }
    }.let { list -> if (sorting == "Nome") list.sortedBy { it.text("name") } else {
        val key = when (sorting) { "Receita ↑" -> "revenueGrowth"; "EPS ↑" -> "epsGrowth"; "P/E crescente" -> "pe"; "Variação ↑" -> "changePct"; else -> "score" }
        if (key == "pe") list.sortedBy { it.number(key) ?: Double.POSITIVE_INFINITY } else list.sortedByDescending { it.number(key) ?: Double.NEGATIVE_INFINITY }
    } }
    ModeBanner(state, model)
    Text("Descobre padrões", style = MaterialTheme.typography.headlineSmall)
    CollectionChips(listOf("Favoritos", "Carteira"), universe, "screener-universe") { universe = it }
    FinanceCard {
        val loaded = symbols.count { it in state.screening }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("$loaded / ${symbols.size}", style = MaterialTheme.typography.headlineMedium); Text("instrumentos analisados", style = MaterialTheme.typography.labelSmall) }
            FilledTonalButton({ model.screenSymbols(symbols) }, Modifier.testTag("run-screener"), enabled = !state.screeningBusy && symbols.isNotEmpty()) { Text(if (state.screeningBusy) "A analisar…" else "Analisar ativos") }
        }
        if (state.screeningBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("A análise usa pedidos dos providers. Filtros e comparações usam os dados já carregados.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    CollectionSearch(query, { query = it }, "screener-filter")
    CollectionChips(listOf("Todos", "Receita e EPS ↑", "P/E positivo", "Cobertura ≥75%"), preset, "screener-preset") { preset = it }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ showFilters = !showFilters }, Modifier.testTag("screener-filters")) { Text(if (showFilters) "Ocultar filtros" else "Mais filtros") }
        PlanChoice("Ordenar", listOf("Score", "Nome", "Receita ↑", "EPS ↑", "P/E crescente", "Variação ↑"), sorting, "screener-sort") { sorting = it }
    }
    if (showFilters) FinanceCard {
        SectorMenu(symbols.mapNotNull { directory[it]?.sector }, sector, "screener-sector") { sector = it }
        CollectionChips(listOf("Todos", "Ação", "ETF", "Desconhecido"), type, "screener-type") { type = it }
        Text("Score mínimo: ${minimum.toInt()}", style = MaterialTheme.typography.labelMedium)
        Slider(minimum, { minimum = it }, valueRange = 0f..100f, steps = 19, modifier = Modifier.testTag("screener-minimum"))
        TextButton({ minimum = 0f; type = "Todos"; sector = "Todos"; preset = "Todos"; query = "" }, Modifier.testTag("screener-clear")) { Text("Limpar filtros") }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${results.size} resultados", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        OutlinedButton({ compare = true }, enabled = !state.screeningBusy && selected.count { it in state.screening } >= 2, modifier = Modifier.testTag("screener-compare")) { Text("Comparar (${selected.size}/3)") }
    }
    if (results.isEmpty()) FinanceCard { EmptyState("Sem resultados", if (state.screening.isEmpty()) "Seleciona Analisar ativos para começar." else "Nenhum ativo corresponde aos filtros atuais.") }
    results.forEach { snapshot ->
        val ticker = snapshot.text("symbol")
        FinanceCard(modifier = Modifier.testTag("screened-$ticker")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenerScore(snapshot)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp).clickable { model.selectAsset(ticker) }) {
                    Text(snapshot.text("name").ifBlank { ticker }, style = MaterialTheme.typography.titleMedium)
                    Text("$ticker · ${money(snapshot.number("price"), snapshot.text("currency"))}", style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(ticker in selected, { checked -> selected = if (!checked) selected - ticker else (selected + ticker).take(3) }, enabled = ticker in selected || selected.size < 3, modifier = Modifier.testTag("compare-select-$ticker"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinancialStat("P/E", decimal(snapshot.number("pe")), Modifier.weight(1f))
                FinancialStat("Receita", percent(snapshot.number("revenueGrowth")), Modifier.weight(1f))
                FinancialStat("EPS", percent(snapshot.number("epsGrowth")), Modifier.weight(1f))
            }
            Text(if (!snapshot.optBoolean("analysisApplicable", true)) "ETF / fundo · score de empresas não aplicável" else "Score heurístico · cobertura ${decimal(snapshot.number("coverage"), 0)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({ model.selectAsset(ticker) }, Modifier.testTag("screener-open-$ticker")) { Text("Explorar análise →") }
        }
    }
    if (compare) ScreenerComparison(selected.mapNotNull { state.screening[it] }) { compare = false }
}
@Composable private fun ScreenerComparison(rows: List<JSONObject>, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, containerColor = MaterialTheme.colorScheme.surface, title = { Column { Text("Comparar instrumentos"); Text("Desliza ↔ para ver todos os instrumentos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { snapshot ->
                    Column(Modifier.width(156.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(snapshot.text("symbol"), style = MaterialTheme.typography.titleLarge)
                        Text(snapshot.text("name"), style = MaterialTheme.typography.bodySmall, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        FinancialStat("Cotação", money(snapshot.number("price"), snapshot.text("currency")))
                        FinancialStat("Score / 100", decimal(snapshot.number("score"), 0))
                        listOf("pe" to "P/E", "revenueGrowth" to "Receita (%)", "epsGrowth" to "EPS (%)", "roe" to "ROE (%)").forEach { (key, label) ->
                            FinancialStat(label, if (key == "pe") decimal(snapshot.number(key)) else percent(snapshot.number(key)))
                            Text(metricSource(snapshot, key).ifBlank { "Fonte / período não disponíveis" }, style = MaterialTheme.typography.labelSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(close, Modifier.testTag("screener-comparison-close")) { Text("Fechar") } })
}
