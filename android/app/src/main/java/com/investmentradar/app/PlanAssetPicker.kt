package com.investmentradar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable internal fun PlanChoice(label: String, choices: List<String>, value: String, tag: String, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, Modifier.testTag(tag)) { Text("$label: $value ▾") }
        DropdownMenu(expanded, { expanded = false }) { choices.forEach { option ->
            DropdownMenuItem(text = { Text(option) }, onClick = { change(option); expanded = false }, modifier = Modifier.testTag("$tag-$option"))
        } }
    }
}

@Composable internal fun PlanAssetPicker(group: PlanGroup, state: RadarState, model: RadarViewModel, accumulatingDefault: Boolean, dismiss: () -> Unit, choose: (DirectoryAsset) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(if (group.kind in listOf("world", "europe")) "ETF" else if (group.kind in listOf("sector", "stocks")) "Ação" else "Todos") }
    var sector by rememberSaveable { mutableStateOf(if (group.kind == "sector") group.name else "Todos") }
    var region by rememberSaveable { mutableStateOf(if (group.kind == "world") "Mundial" else if (group.kind == "europe") "Europa" else "Todos") }
    var accumulating by rememberSaveable { mutableStateOf(accumulatingDefault) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var currency by rememberSaveable { mutableStateOf("EUR") }
    var selectedMetric by remember { mutableStateOf<String?>(null) }
    var metric by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val extra = (state.watch + state.positions.map { it.symbol }).distinct().filter { directoryAsset(it) == null }.map { symbol ->
        val quote = state.quotes[symbol]
        DirectoryAsset(symbol, quote?.text("name")?.ifBlank { symbol } ?: symbol, "Sem setor", quote?.text("currency").orEmpty(), type = "Desconhecido", region = "Desconhecida", source = "Favoritos / carteira")
    }
    val candidates = (assetDirectory + extra).filter {
        it.matches(query, sector) && (type == "Todos" || it.type == type) && (region == "Todos" || it.region == region) && (!accumulating || it.type != "ETF" || it.accumulating == true)
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Escolher para ${group.name}") }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanInput("Nome, ticker ou ISIN", query, "plan-asset-query", numeric = false) { query = it }
            TextButton({ showFilters = !showFilters }, Modifier.testTag("plan-asset-filters")) { Text(if (showFilters) "Fechar filtros" else "Filtros · $type · $region · $sector") }
            if (showFilters) {
            PlanChoice("Tipo", listOf("Todos", "ETF", "Ação", "Desconhecido"), type, "plan-asset-type") { type = it }
            PlanChoice("Setor", listOf("Todos") + investmentSectors + "ETF diversificados" + "Sem setor", sector, "plan-asset-sector") { sector = it }
            PlanChoice("Região", listOf("Todos", "Mundial", "Europa", "EUA", "Ásia", "Desconhecida"), region, "plan-asset-region") { region = it }
            Row { Checkbox(accumulating, { accumulating = it }, Modifier.testTag("plan-asset-accumulating")); Text("Preferir ETF acumulativos") }
            }
            Text("${candidates.size} correspondências no catálogo e nos teus ativos", style = MaterialTheme.typography.labelMedium)
            candidates.take(12).forEach { asset ->
                HorizontalDivider()
                Text(asset.name, style = MaterialTheme.typography.titleSmall)
                Text("${asset.symbol} · ${asset.currency.ifBlank { "moeda por confirmar" }} · ${asset.region}", style = MaterialTheme.typography.bodySmall)
                if (asset.isin.isNotBlank()) Text(asset.isin, style = MaterialTheme.typography.bodySmall)
                Text("Corresponde aos filtros: ${asset.type} · ${asset.sector}. Fonte: ${asset.source}.", style = MaterialTheme.typography.bodySmall)
                if (asset.type == "ETF") Text("Índice/exposição: ${asset.region} · ${if (asset.accumulating == true) "Acumulação" else "Dividendos por confirmar"}. Custos atuais: por confirmar.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ choose(asset) }, enabled = asset.currency.length == 3, modifier = Modifier.testTag("plan-pick-${asset.symbol}")) { Text("Adicionar") }
                    TextButton({
                        selectedMetric = asset.symbol; metric = null; problem = null; loading = true
                        scope.launch { try { metric = model.planInstrumentMetrics(asset.symbol) } catch (e: CancellationException) { throw e } catch (_: Exception) { problem = "Não foi possível carregar os dados." } finally { loading = false } }
                    }, enabled = !loading, modifier = Modifier.testTag("plan-metrics-${asset.symbol}")) { Text("Ver métricas") }
                }
                if (selectedMetric == asset.symbol) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (problem != null) Text(problem!!)
                    metric?.let { data ->
                        Text(if (data.text("mode") == "demo") "DEMO · valores sintéticos" else "LIVE · dados dos providers", style = MaterialTheme.typography.labelMedium)
                        MetricRow("Cotação", money(data.number("price"), data.text("currency")))
                        val date = data.number("timestamp")?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it.toLong()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) } ?: "data indisponível"
                        Text("${data.optJSONObject("sources")?.text("quote").orEmpty()} · $date", style = MaterialTheme.typography.labelSmall)
                        if (asset.type == "Ação") {
                            val metrics = data.optJSONObject("metrics") ?: data
                            listOf("revenueGrowth" to "Crescimento da receita (%)", "epsGrowth" to "Crescimento do EPS (%)", "pe" to "P/E", "roe" to "ROE (%)", "debtToEquity" to "Dívida / capital próprio").forEach { (key, title) ->
                                MetricRow(title, decimal(metrics.number(key)))
                                data.optJSONObject("metricSources")?.optJSONObject(key)?.let { origin -> Text(listOf(origin.text("source"), origin.text("period"), origin.text("basis")).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        Text("Dados ausentes não confirmam qualidade ou crescimento. Não é uma classificação de oportunidades.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (candidates.size > 12) Text("Refina a pesquisa para ver outros resultados.")
            if (candidates.isEmpty()) Text("Sem correspondências. Altera os filtros ou usa um ticker manual.")
            HorizontalDivider()
            PlanInput("Moeda para ticker manual", currency, "plan-manual-currency", numeric = false) { currency = it.uppercase().take(3) }
            OutlinedButton({ choose(DirectoryAsset(symbol(query), symbol(query), "Sem setor", currency, type = "Desconhecido", region = "Desconhecida", source = "Introdução manual")) }, enabled = validSymbol(symbol(query)) && currency.matches(Regex("[A-Z]{3}")), modifier = Modifier.testTag("plan-manual-add")) { Text("Usar ticker manual") }
            Text("Catálogo inicial, não pesquisa de toda a bolsa. Confirma o ISIN, bolsa e disponibilidade na XTB. A classificação por setor não representa a composição interna de ETF diversificados.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = {}, dismissButton = { TextButton(dismiss) { Text("Fechar") } })
}
