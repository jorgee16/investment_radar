package com.investmentradar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
    val profile = rememberAssetProfile(state)
    if (state.tab == 5) {
        Text("Ficha de referência · informação do catálogo e fontes disponíveis", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("asset-profile-reference"))
        if (snapshot?.text("mode") == "demo") Text("DEMO · métricas e previsões dos painéis financeiros são sintéticas.", style = MaterialTheme.typography.labelSmall)
        FilledTonalButton({ model.openResearch(profile) }, Modifier.testTag("asset-research")) { Text("Comparar e construir a carteira →") }
        AssetKnowledgeScreen(profile, snapshot)
        return
    }
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
        AssetKnowledgeScreen(profile, null)
        return
    }
    when (state.tab) {
        0 -> OverviewScreen(state, model, cached?.let { mergeQuote(snapshot, it) } ?: snapshot, profile, addPosition)
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
    if (state.tab in 0..2) DataAvailability(snapshot) { model.navigate(Page.Providers) }
}

@Composable private fun OverviewScreen(state: RadarState, model: RadarViewModel, snapshot: JSONObject, profile: DirectoryAsset, addPosition: () -> Unit) {
    var candle by rememberSaveable { mutableStateOf(false) }; var expanded by rememberSaveable { mutableStateOf(false) }
    val hasOHLC = state.history.isNotEmpty() && state.history.all { p -> listOf("open", "high", "low", "close").all { p.number(it) != null } }
    val currency = snapshot.text("currency")
    FinanceCard {
        AssetQuoteHeader(state.symbol, snapshot, profile)
        val source = snapshot.optJSONObject("sources")?.text("quote").orEmpty()
        val timestamp = snapshot.number("timestamp")?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochSecond(it.toLong()).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z")) }
        Text("${state.symbol} · ${currency.ifBlank { "moeda desconhecida" }} · ${if (source == "none") "Sem cotação" else source}${timestamp?.let { "\n$it" }.orEmpty()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (snapshot.has("cacheFetchedAt") && !state.demo) Text(quoteCacheLabel(snapshot), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("asset-cache-time"))
        if (state.historyMessage != null && state.history.isNotEmpty()) Text(state.historyMessage,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("history-status"))
        PriceChart(state.history, currency, (state.historyLoading || state.loading) && state.history.isEmpty(), candle && hasOHLC, emptyMessage = state.historyMessage)
        if (state.historyMessage != null && !state.loading && !state.historyLoading) TextButton({ model.setRange(state.days) }, Modifier.testTag("history-retry")) { Text("Tentar novamente") }
        ChartControls(state, model, candle && hasOHLC, { candle = it }, { expanded = true })
        Text(if (state.demo) "Histórico sintético de demonstração; os períodos não representam negociação real." else "Histórico diário. A disponibilidade de velas e datas depende do provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AssetKnowledgeTeaser(profile) { model.setTab(5) }
    snapshot.optJSONObject("priceStatistics")?.let { stats ->
        val low = stats.number("low"); val high = stats.number("high")
        if (low != null && high != null) FinanceCard(title = "Intervalo do histórico") {
            RangeVisual(low, high, stats.number("last"), currency)
            Text("${stats.text("from")} → ${stats.text("to")} · ${stats.text("source")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    OverviewStatistics(snapshot) { model.setTab(1) }
    if (expanded) Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (state.historyMessage != null && state.history.isNotEmpty()) Text(state.historyMessage, style = MaterialTheme.typography.bodySmall)
                PriceChart(state.history, currency, (state.historyLoading || state.loading) && state.history.isEmpty(), candle && hasOHLC, height = 260, emptyMessage = state.historyMessage)
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
    val warnings = snapshot.optJSONArray("warnings")?.strings().orEmpty().distinct()
    if (missing.isEmpty() && warnings.isEmpty()) return
    var expanded by rememberSaveable(snapshot.text("symbol")) { mutableStateOf(false) }
    FinanceCard(title = "Fontes e cobertura") {
        Text(if (missing.isEmpty()) "Dados recebidos · consultar fontes" else "${missing.size} tipos de dados por completar", style = MaterialTheme.typography.bodyMedium)
        TextButton({ expanded = !expanded }, Modifier.testTag("availability-details")) { Text(if (expanded) "Ocultar detalhes −" else "Ver disponibilidade +") }
        if (expanded) {
            missing.forEach { capability ->
                val item = availability.getJSONObject(capability)
                Text(capabilities.getValue(capability), fontWeight = FontWeight.SemiBold)
                val issues = item.optJSONArray("issues")?.objects().orEmpty()
                if (item.text("status") == "missing_key") Text("Nenhum provider configurado.", style = MaterialTheme.typography.bodySmall)
                issues.map { "${it.text("provider")}: ${it.text("message")}" }.distinct().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (missing.isEmpty()) warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        TextButton(configure, Modifier.testTag("availability-providers")) { Text("Ver providers e permissões →") }
    }
}

@Composable private fun AnalysisScreen(snapshot: JSONObject) {
    val applicable = snapshot.optBoolean("analysisApplicable", true)
    val hasScore = applicable && (snapshot.number("coverage") ?: 0.0) > 0
    var panel by rememberSaveable(snapshot.text("symbol")) { mutableStateOf(if (hasScore) "fundamental" else "market") }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("market" to "Mercado", "fundamental" to "Fundamentos", "analysts" to "Analistas").forEach { (key, label) ->
            FilterChip(panel == key, { panel = key }, { Text(label) }, Modifier.testTag("analysis-$key"))
        }
    }
    if (panel == "analysts") {
        AnalystDashboard(snapshot)
        EarningsDashboard(snapshot)
        if (snapshot.optJSONObject("analysts") == null && snapshot.optJSONArray("earningsHistory")?.length().let { it == null || it == 0 })
            FinanceCard { EmptyState("Sem consenso disponível", "Os providers não devolveram recomendações ou estimativas para este ativo.") }
        return
    }
    if (panel == "market") {
        HistoricalDashboard(snapshot)
        if (snapshot.optJSONObject("priceStatistics") == null) FinanceCard { EmptyState("Histórico insuficiente", "São necessários pelo menos dois fechos comparáveis para a análise de mercado.") }
        if (hasScore) return
    }
    if (!hasScore) {
        FinanceCard(title = if (applicable) "Análise fundamental indisponível" else if (snapshot.text("instrumentType") == "note") "Análise de ETC / ETN" else "Análise de ETF / fundo") {
            Text(if (applicable) "Score e valor justo precisam de fundamentais. Explora o histórico no painel Mercado."
                else "Explora o histórico em Mercado. O modelo de empresas não se aplica a este instrumento.", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    ScoreDashboard(snapshot)
    if (snapshot.number("fairLow") != null) ValuationDashboard(snapshot)
    val reasons = snapshot.optJSONArray("reasons")?.objects().orEmpty()
    if (reasons.isNotEmpty()) {
        var expanded by rememberSaveable(snapshot.text("symbol")) { mutableStateOf(false) }
        FinanceCard(title = "O que explica a análise") {
            TextButton({ expanded = !expanded }, Modifier.testTag("analysis-explanation")) { Text(if (expanded) "Fechar explicação −" else "Explorar os indicadores +") }
            if (expanded) reasons.forEach { reason ->
                Text(reason.text("label"), fontWeight = FontWeight.SemiBold)
                Text(reason.text("detail"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
@Composable private fun FinancialsScreen(snapshot: JSONObject) {
    val fund = !snapshot.optBoolean("analysisApplicable", true)
    if (fund) FinanceCard(title = if (snapshot.text("instrumentType") == "note") "Perfil do ETC / ETN" else "Perfil do ETF / fundo") {
        MetricRow("Tipo de instrumento", when (snapshot.text("instrumentType")) { "etf" -> "ETF"; "note" -> "ETC/ETN"; else -> "Fundo" })
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
        FinancialHistoryDashboard(snapshot)
        EarningsDashboard(snapshot)
        FinanceCard(title = "Dados financeiros") {
            if (available.isEmpty()) Text("Sem rácios financeiros devolvidos pelos providers para este ativo.", style = MaterialTheme.typography.bodyMedium)
            val sections = linkedMapOf(
                "Avaliação" to listOf("pe", "forwardPE"),
                "Crescimento e rentabilidade" to listOf("revenueGrowth", "epsGrowth", "fcfMargin", "roe"),
                "Balanço e resultados" to listOf("debtToEquity", "earningsSurprise", "estimateRevision")
            )
            sections.forEach { (title, keys) ->
                val rows = available.filterKeys { it in keys }
                if (rows.isNotEmpty()) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MetricTiles(rows.map { (key, label) -> VisualMetric(key, label,
                        if (key in listOf("pe", "forwardPE", "debtToEquity")) decimal(snapshot.number(key)) else percent(snapshot.number(key)),
                        metricSource(snapshot, key), metricExplanation(key), snapshot.number(key)) })
                }
            }
            if (snapshot.text("sector").isNotBlank()) MetricRow("Setor", snapshot.text("sector"))
            if (snapshot.text("reportingCurrency").isNotBlank()) MetricRow("Moeda de reporte", snapshot.text("reportingCurrency"))
        }
    }
    FinanceCard(title = "Dados de negociação") {
        MetricRow("Símbolo", snapshot.text("symbol"))
        MetricRow("Moeda de negociação", snapshot.text("currency").ifBlank { "Desconhecida" })
        if (snapshot.text("instrumentType") == "unknown") Text("Tipo de instrumento por confirmar. Se for um ETF, são necessários dados do fundo, não demonstrações financeiras de uma empresa.", style = MaterialTheme.typography.bodySmall)
        Text("A moeda de negociação pode ser diferente da moeda de reporte ou da moeda base do fundo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
