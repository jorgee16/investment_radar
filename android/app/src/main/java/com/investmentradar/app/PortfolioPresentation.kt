package com.investmentradar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.hypot

internal data class HoldingGroup(val symbol: String, val currency: String, val lots: List<Position>) {
    val quantity get() = lots.sumOf { it.qty }
    val invested get() = lots.sumOf { it.qty * it.avg }
}
private data class HoldingSlice(val label: String, val amount: Double)

@Composable internal fun PortfolioScreen(state: RadarState, model: RadarViewModel, add: () -> Unit) {
    TextButton({ model.openResearch() }, Modifier.testTag("portfolio-research")) { Text("Investigar ativos e simular uma adição →") }
    var planTab by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.researchPlanRequested) { if (state.researchPlanRequested) { planTab = true; model.consumeResearchPlanRequest() } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!planTab, { planTab = false }, label = { Text("Investimentos") }, modifier = Modifier.testTag("portfolio-holdings-tab"))
        FilterChip(planTab, { planTab = true }, label = { Text("Plano mensal") }, modifier = Modifier.testTag("portfolio-plan-tab"))
    }
    if (planTab) { InvestmentPlanScreen(model); return }
    var query by rememberSaveable { mutableStateOf("") }; var sector by rememberSaveable { mutableStateOf("Todos") }
    var display by rememberSaveable { mutableStateOf("Por ativo") }; var grouping by rememberSaveable { mutableStateOf("Ativos") }
    val directory = knownAssets(state).associateBy { it.symbol }
    fun metadata(symbol: String) = directory[symbol] ?: DirectoryAsset(symbol, symbol, "Sem setor", "", type = "Desconhecido")
    val visible = state.positions.filter { metadata(it.symbol).matches(query, sector) }
    val groups = visible.groupBy { it.symbol to it.currency }.map { (key, lots) -> HoldingGroup(key.first, key.second, lots) }
        .sortedWith(compareBy<HoldingGroup> { it.currency }.thenByDescending { it.invested })
    val totals = portfolioTotals(state.positions, state.quotes)
    ModeBanner(state, model)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("A tua carteira", style = MaterialTheme.typography.headlineSmall); Text("${state.positions.map { it.symbol }.distinct().size} instrumentos · ${state.positions.size} compras", style = MaterialTheme.typography.bodySmall) }
        FilledTonalButton(add, Modifier.testTag("add-position")) { Text("+ Compra") }
    }
    if (totals.isEmpty()) FinanceCard { EmptyState("Constrói a tua carteira", "Regista uma compra para veres o património, os resultados e a distribuição. Podes criar o teu plano mensal no separador ao lado.") }
    totals.forEach { total -> FinanceCard(title = "Património · ${total.currency}", modifier = Modifier.testTag("total-${total.currency}")) {
        val profit = total.value?.minus(total.invested)
        Text(money(total.value, total.currency), style = MaterialTheme.typography.headlineLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FinancialStat("Capital investido", money(total.invested, total.currency), Modifier.weight(1f).testTag("invested-${total.currency}"))
            FinancialStat("Resultado não realizado", money(profit, total.currency), Modifier.weight(1f).testTag("profit-${total.currency}"), profit?.let { if (it >= 0) FinanceGreen else FinanceRed } ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (total.value == null) Text("Valor incompleto: faltam cotações na moeda da posição.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Montantes por moeda · sem conversão cambial", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
    if (state.positions.isNotEmpty()) {
        FinanceCard(title = "Distribuição do investimento") {
            CollectionChips(listOf("Ativos", "Setores"), grouping, "portfolio-allocation-mode") { grouping = it }
            totals.forEach { total ->
                val rows = state.positions.filter { it.currency == total.currency }
                val slices = rows.groupBy { if (grouping == "Ativos") it.symbol else metadata(it.symbol).sector }.map { (label, lots) -> HoldingSlice(label, lots.sumOf { it.qty * it.avg }) }.sortedByDescending { it.amount }
                HoldingsDonut(slices, total.currency, if (grouping == "Ativos") query else sector.takeUnless { it == "Todos" }) { label ->
                    if (grouping == "Ativos") { query = if (query == label) "" else label; sector = "Todos" }
                    else { sector = if (sector == label) "Todos" else label; query = "" }
                }
            }
            Text("Pesos pelo custo de aquisição. ETF diversificados ficam numa categoria própria; não representam a composição interna dos fundos.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FinanceCard {
            Text("Posições e compras", style = MaterialTheme.typography.titleLarge)
            CollectionSearch(query, { query = it }, "portfolio-search")
            SectorMenu(state.positions.map { metadata(it.symbol).sector }, sector, "portfolio-sector") { sector = it }
            CollectionChips(listOf("Por ativo", "Compras"), display, "portfolio-view") { display = it }
            Text("${visible.size} de ${state.positions.size} compras · totais da carteira completa", style = MaterialTheme.typography.labelSmall)
            if (query.isNotEmpty() || sector != "Todos") TextButton({ query = ""; sector = "Todos" }, Modifier.testTag("portfolio-clear")) { Text("Limpar filtros") }
            if (visible.isEmpty()) EmptyState("Sem correspondências", "Experimenta outro nome, ISIN ou setor.")
        }
    }
    if (display == "Por ativo") groups.forEach { group ->
        val asset = metadata(group.symbol)
        val quote = state.quotes[group.symbol]
        val price = quote?.number("price")?.takeIf { quote.text("currency") == group.currency }
        val value = price?.times(group.quantity); val result = value?.minus(group.invested)
        FinanceCard(modifier = Modifier.testTag("holding-${group.symbol}-${group.currency}")) {
            Column(Modifier.fillMaxWidth().clickable { model.selectAsset(group.symbol) }) {
                Text(asset.name, style = MaterialTheme.typography.titleMedium)
                Text("${group.symbol} · ${group.currency} · ${asset.sector}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FinancialStat("Valor de mercado", money(value, group.currency), Modifier.weight(1f))
                FinancialStat("Resultado não realizado", money(result, group.currency), Modifier.weight(1f), result?.let { if (it >= 0) FinanceGreen else FinanceRed } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FinancialStat("Unidades", decimal(group.quantity, 4), Modifier.weight(1f))
                FinancialStat("Custo médio", money(group.invested / group.quantity, group.currency), Modifier.weight(1f))
            }
            if (quote?.number("cacheFetchedAt") != null && !state.demo) Text(quoteCacheLabel(quote), style = MaterialTheme.typography.labelSmall)
            TextButton({ display = "Compras"; query = group.symbol; sector = "Todos" }, Modifier.testTag("holding-lots-${group.symbol}-${group.currency}")) { Text("Ver ${group.lots.size} ${if (group.lots.size == 1) "compra" else "compras"} →") }
        }
    } else visible.forEach { position ->
        val quote = state.quotes[position.symbol]; val price = quote?.number("price")?.takeIf { quote.text("currency") == position.currency }
        FinanceCard(modifier = Modifier.testTag("position-${position.id}")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { model.selectAsset(position.symbol) }) {
                    Text(metadata(position.symbol).name, style = MaterialTheme.typography.titleMedium)
                    Text("${position.symbol} · ${decimal(position.qty, 4)} unidades", style = MaterialTheme.typography.bodySmall)
                }
                IconButton({ model.removePosition(position.id) }, Modifier.testTag("remove-position-${position.id}")) { Icon(Icons.Default.Delete, "Remover compra de ${position.symbol}") }
            }
            MetricRow("Investido", money(position.avg * position.qty, position.currency))
            MetricRow("Custo médio", money(position.avg, position.currency))
            MetricRow("Resultado não realizado", money(price?.let { (it - position.avg) * position.qty }, position.currency))
            MetricRow("Valor de mercado", money(price?.times(position.qty), position.currency))
            TextButton({ model.selectAsset(position.symbol) }) { Text("Abrir análise →") }
        }
    }
}

@Composable private fun HoldingsDonut(slices: List<HoldingSlice>, currency: String, selected: String?, select: (String) -> Unit) {
    val total = slices.sumOf { it.amount }
    if (total <= 0 || !total.isFinite()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 280.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.3f) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(152.dp)) { HoldingRing(slices, total, currency, selected, select, 152) }
                Column(Modifier.weight(1f)) { HoldingLegend(slices, total, currency, selected, select) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldingRing(slices, total, currency, selected, select, 176)
                HoldingLegend(slices, total, currency, selected, select)
            }
        }
    }
}
@Composable private fun HoldingRing(slices: List<HoldingSlice>, total: Double, currency: String, selected: String?, select: (String) -> Unit, diameter: Int) {
    val text = slices.firstOrNull { it.label == selected }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter.dp).testTag("portfolio-allocation-$currency").semantics { contentDescription = "Distribuição em $currency: " + slices.joinToString { "${it.label} ${decimal(it.amount / total * 100, 1)}%" } }
            .pointerInput(slices, selected) { detectTapGestures { point ->
                val x = point.x - size.width / 2f; val y = point.y - size.height / 2f
                val radius = size.width / 2f
                if (hypot(x, y) < radius * .58f || hypot(x, y) > radius) return@detectTapGestures
                val angle = (Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450) % 360
                var sum = 0.0
                slices.firstOrNull { sum += it.amount / total * 360; angle <= sum }?.let { select(it.label) }
            } }) {
            var start = -90f
            slices.forEachIndexed { i, slice ->
                val sweep = (slice.amount / total * 360).toFloat()
                drawArc(PlanPalette[i % PlanPalette.size].copy(alpha = if (selected.isNullOrEmpty() || selected == slice.label) 1f else .35f), start, sweep, false, style = Stroke(22.dp.toPx(), cap = StrokeCap.Butt), topLeft = Offset(16.dp.toPx(), 16.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width - 32.dp.toPx(), size.height - 32.dp.toPx()))
                start += sweep
            }
        }
        Column(Modifier.width((diameter * .56f).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text?.label ?: currency, style = MaterialTheme.typography.labelMedium, maxLines = 2)
            Text(if (text == null) money(total, currency) else "${decimal(text.amount / total * 100, 1)}%", style = MaterialTheme.typography.titleMedium)
            Text("Capital investido", style = MaterialTheme.typography.labelSmall)
        }
    }
}
@Composable private fun HoldingLegend(slices: List<HoldingSlice>, total: Double, currency: String, selected: String?, select: (String) -> Unit) {
    slices.forEachIndexed { i, slice ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("portfolio-slice-$currency-${slice.label}").clickable { select(slice.label) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(Modifier.size(9.dp), color = PlanPalette[i % PlanPalette.size]) {}
            Column(Modifier.weight(1f)) {
                Text(slice.label, style = MaterialTheme.typography.labelMedium, fontWeight = if (selected == slice.label) FontWeight.Bold else FontWeight.Normal)
                Text("${decimal(slice.amount / total * 100, 1)}% · ${money(slice.amount, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
