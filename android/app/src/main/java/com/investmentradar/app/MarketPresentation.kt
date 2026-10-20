package com.investmentradar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** Presentation only: values are supplied by the existing core/cache, with no invented trends. */
@Composable internal fun ChangeBadge(change: Double?, modifier: Modifier = Modifier) {
    val color = change?.let { if (it >= 0) FinanceGreen else FinanceRed } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = color.copy(alpha = .10f)) {
        Text(percent(change), Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable internal fun PortfolioHero(state: RadarState, open: () -> Unit) {
    val totals = portfolioTotals(state.positions, state.quotes)
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("home-portfolio-hero")) {
        Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f), MaterialTheme.colorScheme.surface))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PlanEyebrow("Património investido")
            if (totals.isEmpty()) {
                Text("O teu futuro,\num investimento de cada vez.", style = MaterialTheme.typography.headlineSmall)
                Text("Organiza a carteira e define o teu plano mensal.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else totals.forEachIndexed { index, total ->
                if (index > 0) HorizontalDivider()
                Text(money(total.value, total.currency), style = MaterialTheme.typography.headlineLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Investido · ${total.currency}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(total.invested, total.currency), style = MaterialTheme.typography.titleSmall)
                    }
                    Column(Modifier.weight(1f)) {
                        val result = total.value?.minus(total.invested)
                        Text("Resultado não realizado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(result, total.currency), style = MaterialTheme.typography.titleSmall, color = result?.let { if (it >= 0) FinanceGreen else FinanceRed } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FilledTonalButton(open, Modifier.testTag("open-portfolio")) { Text(if (totals.isEmpty()) "Começar a minha carteira →" else "Abrir carteira →") }
        }
    }
}

@Composable internal fun WatchMarketPulse(state: RadarState) {
    val changes = state.watch.mapNotNull { state.quotes[it]?.number("changePct") }
    val rising = changes.count { it > 0 }; val falling = changes.count { it < 0 }
    val unchanged = changes.size - rising - falling
    val missing = state.watch.size - changes.size
    FinanceCard(title = "Pulso dos favoritos", modifier = Modifier.testTag("watch-market-pulse")) {
        Text("Variação reportada nas cotações disponíveis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val segments = listOf(rising to FinanceGreen, falling to FinanceRed, unchanged to MaterialTheme.colorScheme.onSurfaceVariant, missing to MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "$rising a subir, $falling a descer, $unchanged sem variação e $missing sem dados" }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            segments.filter { it.first > 0 }.forEach { (count, color) -> Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color, RoundedCornerShape(4.dp))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("A subir" to rising, "A descer" to falling, "Sem variação" to unchanged, "Sem dados" to missing).forEachIndexed { index, (label, count) ->
                Column(Modifier.weight(1f)) {
                    Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = segments[index].second)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Cada cotação mantém a sua data. Este painel não é um índice de mercado.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun WatchlistDashboard(state: RadarState, model: RadarViewModel, add: () -> Unit) {
    var sort by rememberSaveable { mutableStateOf("watch") }
    val ordered = when (sort) {
        "up" -> state.watch.sortedByDescending { state.quotes[it]?.number("changePct") ?: Double.NEGATIVE_INFINITY }
        "down" -> state.watch.sortedBy { state.quotes[it]?.number("changePct") ?: Double.POSITIVE_INFINITY }
        else -> state.watch
    }
    FinanceCard(modifier = Modifier.testTag("home-watchlist")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Os teus favoritos", style = MaterialTheme.typography.titleLarge)
                Text("${state.watch.size} ativos acompanhados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(add, Modifier.testTag("add-watch")) { Text("+ Adicionar") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("watch" to "A minha lista", "up" to "Maiores subidas", "down" to "Maiores descidas").forEach { (key, label) ->
                FilterChip(sort == key, { sort = key }, { Text(label) }, Modifier.testTag("watch-sort-$key"))
            }
        }
        if (ordered.isEmpty()) EmptyState("Ainda sem ativos", "Adiciona um ticker para acompanhares o mercado.")
        ordered.forEachIndexed { index, ticker ->
            PriceRow(ticker, state.quotes[ticker], { model.selectAsset(ticker) })
            if (index < ordered.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
        }
        TextButton({ model.navigate(Page.Watchlist) }) { Text("Gerir watchlist →") }
    }
}

@Composable internal fun AssetQuoteHeader(symbol: String, snapshot: JSONObject) {
    val name = snapshot.text("name").ifBlank { symbol }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(name.take(1).uppercase(), Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(listOf(symbol, snapshot.text("sector")).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text(money(snapshot.number("price"), snapshot.text("currency")), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("asset-price"))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChangeBadge(snapshot.number("changePct"))
        Text("Variação da cotação", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun OverviewStatistics(snapshot: JSONObject, openAnalysis: () -> Unit) {
    val applicable = snapshot.optBoolean("analysisApplicable", true)
    FinanceCard(title = "Em números") {
        if (applicable) {
            val fields = listOf("pe" to "P/E", "forwardPE" to "P/E estimado", "revenueGrowth" to "Crescimento da receita", "epsGrowth" to "Crescimento do EPS")
            val available = fields.filter { snapshot.number(it.first) != null }
            if (available.isNotEmpty()) MetricTiles(available.map { (key, label) -> VisualMetric(key, label,
                if (key in listOf("pe", "forwardPE")) decimal(snapshot.number(key)) else percent(snapshot.number(key)), metricSource(snapshot, key), metricExplanation(key), snapshot.number(key)) })
            else Text("Rácios por disponibilizar", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Score Investment Radar", style = MaterialTheme.typography.labelSmall)
                    Text(snapshot.number("score")?.let { "${decimal(it, 0)} / 100" } ?: "Dados insuficientes", style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Cobertura de métricas", style = MaterialTheme.typography.labelSmall)
                    Text(snapshot.number("coverage")?.let { "${decimal(it, 0)}%" } ?: "—", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            PlanPill(if (snapshot.text("instrumentType") == "etf") "ETF" else "Fundo", active = true)
            Text("O score de empresas não se aplica a este instrumento.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(openAnalysis) { Text("Explorar análise →") }
    }
}
