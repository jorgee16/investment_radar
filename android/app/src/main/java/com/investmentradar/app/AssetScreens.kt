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
    val cached = state.quotes[state.symbol]?.takeIf { it.number("cacheFetchedAt") != null && it.number("price") != null }
    if (snapshot == null) {
        if (cached != null) FinanceCard(title = state.symbol) {
            Text(money(cached.number("price"), cached.text("currency")), fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("cached-asset-price"))
            Text(quoteCacheLabel(cached), style = MaterialTheme.typography.bodySmall)
            Text("A análise completa é carregada separadamente.", style = MaterialTheme.typography.bodySmall)
        }
        if (!state.loading) EmptyState("Análise indisponível", "Atualiza os dados ou verifica a configuração dos providers.")
        return
    }
    if (state.tab in 1..2) DataAvailability(snapshot) { model.navigate(Page.Providers) }
    when (state.tab) {
        0 -> OverviewScreen(state, model, cached?.let { mergeQuote(snapshot, it) } ?: snapshot, addPosition)
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(money(snapshot.number("price"), currency), fontWeight = FontWeight.Bold, fontSize = 38.sp, modifier = Modifier.testTag("asset-price"))
            val change = snapshot.number("changePct")
            Text(percent(change), color = if (change == null) MaterialTheme.colorScheme.onSurfaceVariant else if (change >= 0) FinanceGreen else FinanceRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        val source = snapshot.optJSONObject("sources")?.text("quote").orEmpty()
        val timestamp = snapshot.number("timestamp")?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it.toLong()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z")) }
        Text("${state.symbol} · ${currency.ifBlank { "moeda desconhecida" }} · ${if (source == "none") "Sem cotação" else source}${timestamp?.let { "\n$it" }.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (snapshot.has("cacheFetchedAt") && !state.demo) Text(quoteCacheLabel(snapshot), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("asset-cache-time"))
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
        if (!snapshot.optBoolean("analysisApplicable", true)) {
            MetricRow("Tipo de instrumento", if (snapshot.text("instrumentType") == "etf") "ETF" else "Fundo")
            Text("O score de empresas não se aplica a este instrumento.", style = MaterialTheme.typography.bodySmall)
        } else {
            MetricRow("P/E", decimal(snapshot.number("pe"))); MetricRow("P/E estimado", decimal(snapshot.number("forwardPE")))
            MetricRow("Score Investment Radar", snapshot.number("score")?.let { decimal(it, 0) + " / 100" } ?: "Dados insuficientes")
            MetricRow("Cobertura de métricas", decimal(snapshot.number("coverage"), 0) + "%")
        }
        TextButton({ model.setTab(1) }) { Text("Ver análise →") }
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
@Composable private fun DataAvailability(snapshot: JSONObject, configure: () -> Unit) {
    val availability = snapshot.optJSONObject("availability") ?: return
    val missing = listOf("fundamentals", "estimates").filter {
        availability.optJSONObject(it)?.text("status") in listOf("unavailable", "missing_key")
    }
    if (missing.isEmpty()) return
    FinanceCard(title = "Disponibilidade dos dados") {
        Text(if (availability.optJSONObject("quotes")?.text("status") == "available")
            "A cotação está disponível. Isso não confirma acesso aos fundamentais ou às estimativas."
            else "Ainda não há dados suficientes para esta análise.")
        missing.forEach { capability ->
            val item = availability.getJSONObject(capability)
            val issues = item.optJSONArray("issues")?.objects().orEmpty()
            Text(capabilities.getValue(capability), fontWeight = FontWeight.SemiBold)
            if (item.text("status") == "missing_key") Text("Nenhum provider configurado para estes dados.", style = MaterialTheme.typography.bodySmall)
            else if (issues.isEmpty()) Text("Os providers não devolveram dados para este ativo. Verifica a cobertura do símbolo e do plano.", style = MaterialTheme.typography.bodySmall)
            else issues.map { "${it.text("provider")}: ${it.text("message")}" }.distinct().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text("O teste de cotação valida apenas a cotação. Na página Providers podes testar as restantes capacidades.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(configure, Modifier.testTag("availability-providers")) { Text("Ver providers e permissões →") }
    }
}

@Composable private fun HistoricalAnalysis(snapshot: JSONObject) {
    val stats = snapshot.optJSONObject("priceStatistics") ?: return
    FinanceCard(title = "Comportamento do preço") {
        Text("${stats.text("from")} → ${stats.text("to")}", style = MaterialTheme.typography.bodySmall)
        MetricRow("Variação no período", percent(stats.number("changePct")))
        MetricRow("Menor preço observado", money(stats.number("low"), snapshot.text("currency")))
        MetricRow("Maior preço observado", money(stats.number("high"), snapshot.text("currency")))
        Text("${stats.optInt("observations")} observações · ${stats.text("source")}", style = MaterialTheme.typography.labelSmall)
        Text("Calculado sobre os preços do histórico devolvido pelo provider. Não representa rentabilidade total com dividendos, score ou recomendação de investimento.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AnalysisScreen(snapshot: JSONObject) {
    val applicable = snapshot.optBoolean("analysisApplicable", true)
    if (!applicable || snapshot.number("coverage") == 0.0) {
        FinanceCard(title = if (applicable) "Análise fundamental indisponível" else "Análise de ETF / fundo") {
            Text(if (applicable) "Faltam fundamentais e estimativas para calcular o score e o intervalo de valor justo. A cotação e o histórico, por si só, não bastam."
                else "Este instrumento é um fundo. O modelo de score e valor justo baseado nos resultados de uma empresa não se aplica.")
            Text("Os dados ausentes não são substituídos por valores de demonstração.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HistoricalAnalysis(snapshot)
        Warnings(snapshot)
        return
    }

    FinanceCard(title = "Análise Investment Radar") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("Score heurístico", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(snapshot.number("score")?.let { decimal(it, 0) + "/100" } ?: "—", fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("analysis-score")) }
            Column(Modifier.weight(1f)) { Text("Cobertura", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(decimal(snapshot.number("coverage"), 0) + "%", fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        }
        Text(snapshot.text("thesis"), style = MaterialTheme.typography.bodyMedium)
        Text("Avaliação quantitativa do Investment Radar. Não representa consenso ou recomendação de analistas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HistoricalAnalysis(snapshot)
    FinanceCard(title = "Componentes do score") {
        linkedMapOf("quality" to "Qualidade", "growth" to "Crescimento", "valuation" to "Valuation", "momentum" to "Momentum", "risk" to "Risco").forEach { (key, label) ->
            val value = snapshot.optJSONObject("breakdown")?.number(key)
            if (value != null) MetricRow(label, decimal(value, 0))
            if (value != null) LinearProgressIndicator(progress = { (value / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
        }
    }
    if (snapshot.number("fairLow") != null) FinanceCard(title = "Intervalo de valor justo") {
        val currency = snapshot.text("currency")
        MetricRow("Abaixo de (cenário de compra)", money(snapshot.number("buyBelow"), currency))
        MetricRow("Valor justo — limite inferior", money(snapshot.number("fairLow"), currency))
        MetricRow("Valor justo — limite superior", money(snapshot.number("fairHigh"), currency))
        MetricRow("Acima de (cenário caro)", money(snapshot.number("expensiveAbove"), currency))
        Text("Modelo de múltiplos de resultados. Valores ausentes permanecem indisponíveis; não são preços-alvo de analistas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val reasons = snapshot.optJSONArray("reasons")?.objects().orEmpty()
    if (reasons.isNotEmpty()) FinanceCard(title = "O que explica a análise") { reasons.forEach { reason ->
        Text(reason.text("label"), fontWeight = FontWeight.SemiBold); Text(reason.text("detail"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
    Warnings(snapshot)
}
@Composable private fun FinancialsScreen(snapshot: JSONObject) {
    val fund = !snapshot.optBoolean("analysisApplicable", true)
    if (fund) FinanceCard(title = "Perfil do ETF / fundo") {
        MetricRow("Tipo de instrumento", if (snapshot.text("instrumentType") == "etf") "ETF" else "Fundo")
        val profile = snapshot.optJSONObject("fund")
        val fields = linkedMapOf("isin" to "ISIN", "issuer" to "Gestora", "domicile" to "Domicílio", "index" to "Índice de referência", "inceptionDate" to "Data de lançamento")
        fields.forEach { (key, label) ->
            val value = profile?.text(key).orEmpty()
            if (value.isNotBlank()) MetricRow(label, value, source = snapshot.optJSONObject("metricSources")?.optJSONObject(key)?.text("source"))
        }
        if (fields.keys.none { !profile?.text(it).isNullOrBlank() }) Text("O provider identificou o fundo, mas não devolveu o respetivo perfil. Verifica a cobertura e as permissões do plano.")
        Text("Receita, EPS, ROE e dívida de uma empresa não descrevem este fundo. O P/E da carteira exige dados agregados das participações.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        val fields = linkedMapOf("pe" to "P/E", "forwardPE" to "P/E estimado", "revenueGrowth" to "Crescimento da receita", "epsGrowth" to "Crescimento do EPS", "fcfMargin" to "Margem de fluxo de caixa livre", "roe" to "Retorno sobre o capital (ROE)", "debtToEquity" to "Dívida / capital próprio", "earningsSurprise" to "Surpresa de resultados", "estimateRevision" to "Revisão de estimativas")
        val available = fields.filterKeys { snapshot.number(it) != null }
        FinanceCard(title = "Dados financeiros") {
            if (snapshot.text("sector").isNotBlank()) MetricRow("Setor", snapshot.text("sector"))
            if (snapshot.text("reportingCurrency").isNotBlank()) MetricRow("Moeda de reporte", snapshot.text("reportingCurrency"))
            if (available.isEmpty()) Text("Os providers não devolveram métricas financeiras para este ativo. Não é possível preencher esta secção apenas com cotações.")
            available.forEach { (key, label) ->
                val metric = snapshot.optJSONObject("metricSources")?.optJSONObject(key)
                val source = metric?.let { listOf(it.text("source"), it.text("period"), it.text("basis")).filter(String::isNotBlank).joinToString(" · ") }
                    ?: snapshot.optJSONObject("sources")?.text(if (key in listOf("earningsSurprise", "estimateRevision")) "estimates" else "fundamentals")?.takeIf { it != "none" }
                MetricRow(label, if (key in listOf("pe", "forwardPE", "debtToEquity")) decimal(snapshot.number(key)) else percent(snapshot.number(key)), source = source)
                HorizontalDivider()
            }
            val absent = fields.filterKeys { snapshot.number(it) == null }.values
            if (available.isNotEmpty() && absent.isNotEmpty()) Text("Sem dados: ${absent.joinToString(", ")}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    FinanceCard(title = "Dados de negociação") {
        MetricRow("Símbolo", snapshot.text("symbol"))
        MetricRow("Moeda de negociação", snapshot.text("currency").ifBlank { "Desconhecida" })
        if (snapshot.text("instrumentType") == "unknown") Text("Tipo de instrumento por confirmar. Se for um ETF, são necessários dados do fundo, não demonstrações financeiras de uma empresa.", style = MaterialTheme.typography.bodySmall)
        Text("A moeda de negociação pode ser diferente da moeda de reporte ou da moeda base do fundo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
