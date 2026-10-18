package com.investmentradar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

@Composable internal fun AssetScreen(state: RadarState, model: RadarViewModel, addPosition: () -> Unit) {
    val snapshot = state.snapshot
    ModeBanner(state, model)
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("asset-loading")); Text("A carregar ${state.symbol}…") }
    if (snapshot == null) {
        if (!state.loading) EmptyState("Análise indisponível", "Atualiza os dados ou verifica a configuração dos providers.")
        return
    }
    when (state.tab) {
        0 -> OverviewScreen(state, model, snapshot, addPosition)
        1 -> AnalysisScreen(snapshot)
        2 -> FinancialsScreen(snapshot)
        3 -> EventsList(state.events, state.auxiliaryLoading) { model.selectAsset(it) }
        4 -> {
            if (state.auxiliaryLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val context = LocalContext.current
            if (state.news.isEmpty() && !state.auxiliaryLoading) FinanceCard { EmptyState("Sem notícias disponíveis", "O provider pode não cobrir este ativo ou o plano pode não incluir notícias.") }
            state.news.forEach { article -> FinanceCard {
                Text(article.text("title"), fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                Text("${article.text("date")} · ${article.text("source")}", style = MaterialTheme.typography.labelMedium)
                val uri = android.net.Uri.parse(article.text("url"))
                if (ProviderPolicy.mayOpenExternal(uri.scheme, uri.host, true, true)) TextButton({ openArticle(context, article.text("url")) }) { Text("Ler notícia ↗") }
            } }
        }
    }
}

@Composable private fun OverviewScreen(state: RadarState, model: RadarViewModel, snapshot: JSONObject, addPosition: () -> Unit) {
    var candle by rememberSaveable { mutableStateOf(false) }; var expanded by rememberSaveable { mutableStateOf(false) }
    val hasOHLC = state.history.isNotEmpty() && state.history.all { p -> listOf("open", "high", "low", "close").all { p.number(it) != null } }
    val currency = snapshot.text("currency")
    FinanceCard {
        Text(snapshot.text("name").ifBlank { state.symbol }, fontWeight = FontWeight.SemiBold, fontSize = 25.sp)
        Text(money(snapshot.number("price"), currency), fontWeight = FontWeight.Bold, fontSize = 38.sp, modifier = Modifier.testTag("asset-price"))
        val change = snapshot.number("changePct")
        Text(percent(change), color = if (change == null) MaterialTheme.colorScheme.onSurfaceVariant else if (change >= 0) FinanceGreen else FinanceRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        val source = snapshot.optJSONObject("sources")?.text("quote").orEmpty()
        val timestamp = snapshot.number("timestamp")?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it.toLong()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z")) }
        Text("${state.symbol} · ${currency.ifBlank { "moeda desconhecida" }} · ${if (source == "none") "Sem cotação" else source}${timestamp?.let { "\n$it" }.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        PriceChart(state.history, currency, state.historyLoading, candle && hasOHLC)
        ChartControls(state, model, candle && hasOHLC, { candle = it }, { expanded = true })
        Text(if (state.demo) "Histórico sintético de demonstração; os períodos não representam negociação real." else "Histórico diário. A disponibilidade de velas e datas depende do provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FinanceCard(title = "Os meus investimentos") {
        val holdings = state.positions.filter { it.symbol == state.symbol }
        if (holdings.isEmpty()) { Text("Ainda sem posição neste ativo.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else holdings.groupBy { it.currency }.forEach { (unit, rows) ->
            val qty = rows.sumOf { it.qty }; val cost = rows.sumOf { it.qty * it.avg }
            val value = snapshot.number("price")?.takeIf { currency == unit }?.times(qty)
            MetricRow("Unidades", decimal(qty, 4)); MetricRow("Custo médio", money(cost / qty, unit)); MetricRow("Valor de mercado", money(value, unit))
            MetricRow("Lucro total", money(value?.minus(cost), unit));
        }
        TextButton(addPosition, Modifier.testTag("asset-add-position")) { Text("Adicionar investimento +") }
    }
    FinanceCard(title = "Principais estatísticas") {
        MetricRow("P/E", decimal(snapshot.number("pe"))); MetricRow("P/E estimado", decimal(snapshot.number("forwardPE")))
        MetricRow("Score Investment Radar", snapshot.number("score")?.let { decimal(it, 0) + " / 100" } ?: "Dados insuficientes")
        MetricRow("Cobertura de métricas", decimal(snapshot.number("coverage"), 0) + "%")
        TextButton({ model.setTab(1) }) { Text("Ver análise e valuation →") }
    }
    Warnings(snapshot)
    if (expanded) Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                PriceChart(state.history, currency, state.historyLoading, candle && hasOHLC, height = 260)
                TextButton({ expanded = false }, Modifier.testTag("close-chart")) { Text("Fechar gráfico") }
            }
        }
    }
}

@Composable internal fun MetricRow(label: String, value: String, modifier: Modifier = Modifier, source: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
        if (!source.isNullOrBlank()) Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun AnalysisScreen(snapshot: JSONObject) {
    FinanceCard(title = "Análise Investment Radar") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("Score heurístico", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(snapshot.number("score")?.let { decimal(it, 0) + "/100" } ?: "—", fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("analysis-score")) }
            Column(Modifier.weight(1f)) { Text("Cobertura", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(decimal(snapshot.number("coverage"), 0) + "%", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        }
        Text(snapshot.text("thesis"), style = MaterialTheme.typography.bodyMedium)
        Text("Avaliação quantitativa do Investment Radar. Não representa consenso ou recomendação de analistas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FinanceCard(title = "Componentes do score") {
        linkedMapOf("quality" to "Qualidade", "growth" to "Crescimento", "valuation" to "Valuation", "momentum" to "Momentum", "risk" to "Risco").forEach { (key, label) ->
            val value = snapshot.optJSONObject("breakdown")?.number(key)
            MetricRow(label, decimal(value, 0))
            if (value != null) LinearProgressIndicator(progress = { (value / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
        }
    }
    FinanceCard(title = "Intervalo de valor justo") {
        val currency = snapshot.text("currency")
        MetricRow("Abaixo de (cenário de compra)", money(snapshot.number("buyBelow"), currency))
        MetricRow("Valor justo — limite inferior", money(snapshot.number("fairLow"), currency))
        MetricRow("Valor justo — limite superior", money(snapshot.number("fairHigh"), currency))
        MetricRow("Acima de (cenário caro)", money(snapshot.number("expensiveAbove"), currency))
        Text("Modelo de múltiplos de resultados calculado no core C++. Valores ausentes permanecem indisponíveis; não são preços-alvo de analistas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val reasons = snapshot.optJSONArray("reasons")?.objects().orEmpty()
    if (reasons.isNotEmpty()) FinanceCard(title = "O que explica a análise") { reasons.forEach { reason ->
        Text(reason.text("label"), fontWeight = FontWeight.SemiBold); Text(reason.text("detail"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
    Warnings(snapshot)
}
@Composable private fun FinancialsScreen(snapshot: JSONObject) {
    FinanceCard(title = "Dados financeiros") {
        MetricRow("Setor", snapshot.text("sector").ifBlank { "—" })
        MetricRow("Moeda de reporte", snapshot.text("reportingCurrency").ifBlank { "—" })
        linkedMapOf("pe" to "P/E", "forwardPE" to "P/E estimado", "revenueGrowth" to "Crescimento da receita", "epsGrowth" to "Crescimento do EPS", "fcfMargin" to "Margem de fluxo de caixa livre", "roe" to "Retorno sobre o capital (ROE)", "debtToEquity" to "Dívida / capital próprio", "earningsSurprise" to "Surpresa de resultados", "estimateRevision" to "Revisão de estimativas").forEach { (key, label) ->
            val metric = snapshot.optJSONObject("metricSources")?.optJSONObject(key)
            val source = metric?.let { listOf(it.text("source"), it.text("period"), it.text("basis")).filter(String::isNotBlank).joinToString(" · ") }
                ?: snapshot.optJSONObject("sources")?.text(if (key in listOf("earningsSurprise", "estimateRevision")) "estimates" else "fundamentals")?.takeIf { it != "none" }
            MetricRow(label, if (key in listOf("pe", "forwardPE", "debtToEquity")) decimal(snapshot.number(key)) else percent(snapshot.number(key)), source = source)
            HorizontalDivider()
        }
    }
    FinanceCard(title = "Sobre estes dados") { Text("As métricas são normalizadas a partir dos providers configurados. O plano pode não incluir demonstrações financeiras completas. Dados sintéticos são sempre identificados como Demo.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Warnings(snapshot)
}
@Composable private fun Warnings(snapshot: JSONObject) {
    val warnings = snapshot.optJSONArray("warnings")?.strings().orEmpty().distinct()
    if (warnings.isNotEmpty()) FinanceCard(title = "Disponibilidade dos dados") { warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}
@Composable internal fun EventsList(events: List<JSONObject>, loading: Boolean, open: (String) -> Unit) {
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (events.isEmpty() && !loading) FinanceCard { EmptyState("Sem eventos disponíveis", "Não há resultados devolvidos para o período ou o plano não inclui este calendário.") }
    events.forEach { event -> FinanceCard {
        Text(event.text("date"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(event.text("symbol"), fontWeight = FontWeight.Bold, fontSize = 21.sp)
        Text(event.text("note")); MetricRow("EPS estimado", decimal(event.number("epsEstimated"))); MetricRow("EPS realizado", decimal(event.number("epsActual")))
        Text("Fonte: ${event.text("source")}", style = MaterialTheme.typography.labelSmall)
        TextButton({ open(event.text("symbol")) }) { Text("Abrir ativo →") }
    } }
}
