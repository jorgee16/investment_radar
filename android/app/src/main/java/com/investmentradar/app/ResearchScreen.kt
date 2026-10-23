@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.investmentradar.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable private fun ResearchCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}
@Composable internal fun ResearchEntryCard(state: RadarState, open: () -> Unit) {
    ResearchCard(modifier = Modifier.testTag("research-entry").clickable(onClick = open)) {
        Text("O TEU LABORATÓRIO DE INVESTIMENTOS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("Descobre. Compara. Constrói.", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Catálogo offline · ${state.researchSelection.size}/3 em comparação", style = MaterialTheme.typography.bodySmall)
            Text("Explorar →", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable internal fun ResearchScreen(state: RadarState, model: RadarViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selected = state.researchSelection.map { assetMetadata(state, it) }
    Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)), RoundedCornerShape(18.dp)).padding(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Da descoberta à tua carteira", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Investiga · compara · testa o impacto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    CollectionChips(listOf("Explorar", "Comparar", "Impacto", "Tese"), listOf("Explorar", "Comparar", "Impacto", "Tese")[tab], "research-tab") {
        tab = listOf("Explorar", "Comparar", "Impacto", "Tese").indexOf(it)
    }
    if (selected.isNotEmpty()) ResearchCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${selected.size}/3", style = MaterialTheme.typography.labelSmall)
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                selected.forEach { asset -> InputChip(true, { model.toggleResearch(asset) }, label = { Text("${asset.symbol} ×", style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.testTag("research-remove-${asset.symbol}")) }
            }
            TextButton({ tab = 1 }, Modifier.testTag("research-open-comparison"), contentPadding = PaddingValues(4.dp)) { Text("Comparar", style = MaterialTheme.typography.labelSmall) }
        }
    }
    state.researchMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("research-message")) }
    when (tab) {
        0 -> ResearchExplore(state, model)
        1 -> ResearchComparison(state, model, selected) { tab = 0 }
        2 -> ResearchImpactScreen(state, model, selected) { tab = 0 }
        3 -> ResearchThesisScreen(state, model, selected) { tab = 0 }
    }
}

@Composable private fun ResearchExplore(state: RadarState, model: RadarViewModel) {
    var route by rememberSaveable { mutableStateOf("Todos") }
    var query by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("Todos") }
    var cap by rememberSaveable { mutableStateOf("Todas") }
    var sector by rememberSaveable { mutableStateOf("Todos") }
    var accumulating by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable { mutableStateOf(false) }
    var growth by rememberSaveable { mutableStateOf(false) }
    var shown by rememberSaveable(route, query, country, sector, cap, accumulating, growth) { mutableIntStateOf(10) }
    val strategy = researchRoutes.first { it.title == route }
    val context = LocalContext.current.applicationContext
    val countries by produceState(emptyList<String>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { GlobalCatalogue.manifest(context).getJSONArray("countries").strings() }.getOrDefault(emptyList())
        }
    }
    val catalogue = rememberCataloguePage(listOf(strategy.query, query).filter(String::isNotBlank).joinToString(" "), strategy.type, sector, country, cap)
    val local = knownAssets(state).filter { it.matches(listOf(strategy.query, query).filter(String::isNotBlank).joinToString(" "), sector) && (strategy.type == "Todos" || it.type == strategy.type) && (country == "Todos" || it.country == country) && (cap == "Todas" || it.marketCap == cap) }
    val indexed = catalogue.page.items.associateBy { it.symbol }
    val base = (local.map { indexed[it.symbol] ?: it } + catalogue.page.items).distinctBy { it.symbol }
    val results = base.filter { asset ->
        (!accumulating || asset.accumulating == true) && (!growth || state.researchSnapshots[asset.symbol]?.let { it.text("mode") == (if (state.demo) "demo" else "live") && (it.number("revenueGrowth") ?: Double.NEGATIVE_INFINITY) > 0 && (it.number("epsGrowth") ?: Double.NEGATIVE_INFINITY) > 0 } == true)
    }
    CollectionChips(researchRoutes.map { it.title }, route, "research-route") { route = it; query = ""; accumulating = false; growth = false }
    CollectionSearch(query, { query = it }, "research-search")
    TextButton({ filters = !filters }, Modifier.testTag("research-filters"), contentPadding = PaddingValues(4.dp)) {
        Text(if (filters) "Recolher filtros −" else "Filtros · país, setor, dimensão e métricas +", style = MaterialTheme.typography.labelMedium)
    }
    if (filters) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ResearchCountryChoice(listOf("Todos") + countries, country) { country = it }
        PlanChoice("Setor", listOf("Todos") + investmentSectors + listOf("ETF diversificados", "Outros produtos", "Sem setor"), sector, "research-sector") { sector = it }
        PlanChoice("Dimensão", listOf("Todas", "Mega Cap", "Large Cap", "Mid Cap", "Small Cap", "Micro Cap", "Nano Cap"), cap, "research-cap") { cap = it }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(accumulating, { accumulating = !accumulating }, { Text("Acumulação confirmada") }, Modifier.testTag("research-accumulating"))
        FilterChip(growth, { growth = !growth }, { Text("Receita + EPS ↑ recebidos") }, Modifier.testTag("research-growth"))
    }
    }
    Text(strategy.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (growth && state.demo) PlanPill("DEMO · crescimento sintético")
    if (growth) Text("Filtra apenas métricas já consultadas nesta sessão. Não é um rastreio financeiro de todo o mercado.", style = MaterialTheme.typography.labelSmall)
    Text("${results.size} nesta seleção · ${catalogue.page.matches} correspondências no catálogo", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("research-result-count"))
    if (catalogue.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    catalogue.error?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (!catalogue.loading && results.isEmpty()) EmptyState("Sem correspondências", if (growth) "Seleciona ativos, consulta as métricas em Comparar e volta a aplicar este filtro." else "Experimenta outro nome ou remove filtros. Acumulação desconhecida não é tratada como confirmada.")
    results.take(shown).forEach { asset ->
        ResearchCard(modifier = Modifier.testTag("research-result-${asset.symbol}")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetIdentityIcon(asset)
                Column(Modifier.weight(1f)) {
                    Text(asset.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${asset.symbol} · ${asset.type}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterChip(asset.symbol in state.researchSelection, { model.toggleResearch(asset) }, { Text(if (asset.symbol in state.researchSelection) "✓" else "+") }, Modifier.testTag("research-select-${asset.symbol}"), enabled = asset.symbol in state.researchSelection || state.researchSelection.size < 3)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(asset.sector, if (asset.type == "Ação") asset.country else "", if (asset.accumulating == true) "Acumulação" else "").filter { it.isNotBlank() && it !in listOf("Sem setor", "Desconhecida") }.forEach { PlanPill(it) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ficha de referência", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ model.openResearchReference(asset) }, Modifier.testTag("research-profile-${asset.symbol}"), contentPadding = PaddingValues(4.dp)) { Text("Conhecer →") }
            }
        }
    }
    if (results.size > shown) OutlinedButton({ shown += 10 }, Modifier.fillMaxWidth().testTag("research-show-more")) { Text("Mostrar mais desta seleção") }
}

@Composable private fun ResearchComparison(state: RadarState, model: RadarViewModel, assets: List<DirectoryAsset>, explore: () -> Unit) {
    if (assets.isEmpty()) { ResearchChooseFirst(explore); return }
    ResearchCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Evidência lado a lado", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (state.demo) PlanPill("DEMO")
        }
        Text(if (state.demo) "Valores financeiros sintéticos. A ficha de referência é independente da demonstração." else "Consulta explícita dos ativos selecionados; pode consumir várias chamadas por ativo. A cobertura depende do plano.", style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(model::loadResearchMetrics, enabled = !state.researchBusy, modifier = Modifier.testTag("research-load-metrics")) { Text(if (state.researchBusy) "A consultar…" else "Consultar métricas (${assets.size})") }
        if (state.researchBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        assets.forEach { asset ->
            val received = state.researchSnapshots[asset.symbol]
            val fund = asset.type in listOf("ETF", "Fundo", "ETC/ETN")
            Surface(Modifier.width(168.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(asset.symbol, style = MaterialTheme.typography.labelLarge)
                    FinancialStat(if (fund) "Rendimentos" else "P/E recebido", if (fund) when(asset.accumulating) { true -> "Acumulação"; false -> "Distribuição"; null -> "Por confirmar" } else received?.number("pe")?.let { String.format(java.util.Locale("pt", "PT"), "%.2f", it) } ?: "—")
                    if (!fund) {
                        val keys = listOf("pe", "forwardPE", "revenueGrowth", "epsGrowth", "fcfMargin", "roe", "debtToEquity")
                        val count = keys.count { received?.number(it) != null }
                        Text("Métricas recebidas · $count/7", style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(progress = { count / 7f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    } else Text("Compara índice e estrutura", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    var showReference by rememberSaveable { mutableStateOf(false) }
    ResearchCard {
        TextButton({ showReference = !showReference }, Modifier.testTag("research-reference-toggle"), contentPadding = PaddingValues(4.dp)) { Text(if (showReference) "Recolher identificação e fontes −" else "Identificação e fontes +") }
        val scroll = rememberScrollState()
        Column(Modifier.horizontalScroll(scroll)) {
            Row {
                Text("CRITÉRIO", Modifier.width(118.dp), style = MaterialTheme.typography.labelSmall)
                assets.forEach { asset -> Column(Modifier.width(156.dp).padding(horizontal = 6.dp)) {
                    Text(asset.name, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(asset.symbol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            fun snapshot(a: DirectoryAsset) = state.researchSnapshots[a.symbol]
            val facts: List<Pair<String, (DirectoryAsset) -> String>> = listOf(
                "Tipo" to { a -> a.type }, "Setor da entidade" to { a -> a.sector },
                "País da entidade" to { a -> a.country.ifBlank { "Por confirmar" } },
                "Universo / exposição" to { a -> if (a.type == "Ação") "Receitas: por apurar" else a.region.takeIf { it !in listOf("Desconhecida", "Por confirmar") } ?: "Por confirmar" },
                "Moeda da listagem" to { a -> a.currency },
                "Rendimentos" to { a -> if (a.type !in listOf("ETF", "Fundo")) "Não aplicável" else when(a.accumulating) { true -> "Acumulação"; false -> "Distribuição"; null -> "Por confirmar" } },
                "Índice" to { a -> snapshot(a)?.optJSONObject("fund")?.text("index").orEmpty() },
                "Fonte da ficha" to { a -> a.source },
                "Fonte da cotação" to { a -> snapshot(a)?.optJSONObject("metricSources")?.optJSONObject("price")?.text("source").orEmpty() },
                "Data da cotação" to { a -> snapshot(a)?.number("timestamp")?.let { java.time.Instant.ofEpochSecond(it.toLong()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() }.orEmpty() }
            )
            (if (showReference) facts else facts.filter { it.first in listOf("Tipo", "Universo / exposição", "Rendimentos", "Índice") }).forEach { (label, value) -> ResearchCompareRow(label, assets.map { value(it).ifBlank { "—" } }) }
            listOf("P/E" to "pe", "P/E estimado" to "forwardPE", "Receita ↑ %" to "revenueGrowth", "EPS ↑ %" to "epsGrowth", "Margem FCF %" to "fcfMargin", "ROE %" to "roe", "Dívida / capital" to "debtToEquity").forEach { (label, key) ->
                ResearchCompareRow(label, assets.map { a ->
                    val received = snapshot(a)
                    if (a.type in listOf("ETF", "Fundo", "ETC/ETN")) "Não aplicável¹"
                    else received?.number(key)?.let { number ->
                        val provenance = received.optJSONObject("metricSources")?.optJSONObject(key)
                        val origin = listOf(provenance?.text("source").orEmpty().ifBlank { "Fonte não indicada" }, provenance?.text("period").orEmpty().ifBlank { "Período não indicado" }).joinToString(" · ")
                        String.format(java.util.Locale("pt", "PT"), "%.2f", number) + "\n" + origin.ifBlank { "Fonte/período não indicados" }
                    } ?: "—"
                })
            }
        }
        Text("Desliza a tabela ↔ · — = não recebido · ¹ Modelo de empresa; não avalia fundos. A moeda e o país da entidade não medem exposição cambial/geográfica.", style = MaterialTheme.typography.labelSmall)
    }
    ResearchCard(title = "O que acrescenta à diversificação?") {
        val sameIsin = assets.filter { it.isin.isNotBlank() }.groupBy { it.isin }.values.filter { it.size > 1 }
        if (sameIsin.isNotEmpty()) sameIsin.forEach { group -> Text("Mesmo ISIN: ${group.joinToString { it.symbol }}. São listagens do mesmo instrumento.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("Ter mais tickers não garante diversificação. Compara o índice e as posições dos fundos na ficha; países e setores do catálogo não permitem calcular sobreposição completa.", style = MaterialTheme.typography.bodySmall)
        assets.forEach { a -> TextButton({ model.openResearchReference(a) }, contentPadding = PaddingValues(4.dp), modifier = Modifier.testTag("research-detail-${a.symbol}")) { Text("${a.symbol} · atividade e composição →") } }
    }
    ResearchCard(title = "Antes de escolher") {
        Text("ETFs: confirma custos correntes, réplica, dimensão e índice no documento do emitente. Empresas: compara crescimento, margens e dívida no mesmo período. Um P/E mais baixo, isoladamente, não define uma oportunidade.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun ResearchCompareRow(label: String, values: List<String>) {
    Row(Modifier.padding(vertical = 7.dp)) {
        Text(label, Modifier.width(118.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.forEach { Text(it, Modifier.width(156.dp).padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
}

@Composable private fun ResearchImpactScreen(state: RadarState, model: RadarViewModel, assets: List<DirectoryAsset>, explore: () -> Unit) {
    if (assets.isEmpty()) { ResearchChooseFirst(explore); return }
    var ticker by rememberSaveable { mutableStateOf(assets.first().symbol) }
    val selected = ticker.takeIf { t -> assets.any { it.symbol == t } } ?: assets.first().symbol
    var amount by rememberSaveable { mutableStateOf("350") }
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var planBusy by remember { mutableStateOf(false) }
    var planError by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(selected, amount, state.positions, state.quotes, state.demo) {
        result = null; loading = true
        try { result = model.researchImpact(selected, amount) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { result = JSONObject().put("available", false).put("issues", org.json.JSONArray(listOf("Não foi possível calcular o impacto."))) }
        finally { loading = false }
    }
    ResearchCard(title = "Testa uma adição à carteira") {
        CollectionChips(assets.map { it.symbol }, selected, "research-impact-asset") { ticker = it }
        OutlinedTextField(amount, { amount = it.take(20) }, Modifier.fillMaxWidth().testTag("research-impact-amount"), label = { Text("Montante hipotético · EUR") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        Text("Simulação de aporte em dinheiro, sem registar compras. Os pesos usam as cotações guardadas em EUR; não incluem comissões, impostos nem conversão cambial.", style = MaterialTheme.typography.bodySmall)
        if (state.demo) PlanPill("DEMO · cotações sintéticas")
    }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    val data = result
    if (data != null && data.optBoolean("available")) ResearchCard(title = "Peso por instrumento · antes → depois", modifier = Modifier.testTag("research-impact-result")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FinancialStat("Valor de referência", money(data.number("beforeValue"), "EUR"), Modifier.weight(1f))
            FinancialStat("Após o aporte", money(data.number("afterValue"), "EUR"), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
        }
        data.optJSONArray("rows")?.objects().orEmpty().forEach { row ->
            val before = row.number("before") ?: 0.0
            val after = row.number("after") ?: 0.0
            Row(Modifier.fillMaxWidth()) {
                Text(row.text("symbol"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(String.format(java.util.Locale("pt", "PT"), "%.1f%% → %.1f%%", before, after), style = MaterialTheme.typography.labelLarge)
            }
            ResearchWeightBar(before, MaterialTheme.colorScheme.outlineVariant)
            ResearchWeightBar(after, MaterialTheme.colorScheme.primary)
        }
        Text("Distribuição por instrumentos; não representa as empresas detidas dentro de ETFs nem uma medida de risco.", style = MaterialTheme.typography.labelSmall)
        state.positions.map { it.symbol }.distinct().forEach { sym -> state.quotes[sym]?.let { quote -> Text("$sym · ${quoteCacheLabel(quote)}", style = MaterialTheme.typography.labelSmall) } }
    } else if (data != null) ResearchCard(title = "Faltam dados para simular") {
        data.optJSONArray("issues")?.strings()?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Nenhuma posição foi excluída para apresentar um resultado parcial.", style = MaterialTheme.typography.labelSmall)
    }
    ResearchCard(title = "Transformar a pesquisa num plano") {
        Text("Cria um rascunho com os ativos selecionados e pesos iguais editáveis. Define o horizonte e revê a alocação antes de ativar; pesos iguais não são uma recomendação.", style = MaterialTheme.typography.bodySmall)
        FilledTonalButton({
            planBusy = true; planError = null
            scope.launch {
                try { model.createResearchPlan(amount) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { planError = "Não foi possível criar o rascunho. Tenta novamente." }
                finally { planBusy = false }
            }
        }, enabled = !planBusy, modifier = Modifier.fillMaxWidth().testTag("research-create-plan")) { Text(if (planBusy) "A criar…" else "Criar rascunho com ${assets.size} ativos") }
        planError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    OutlinedButton({ model.navigate(Page.Portfolio) }, Modifier.fillMaxWidth().testTag("research-open-plan")) { Text("Abrir carteira e planos de investimento →") }
}
@Composable private fun ResearchWeightBar(value: Double, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
        if (value > 0) Box(Modifier.fillMaxWidth((value / 100).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
    }
}

@Composable private fun ResearchThesisScreen(state: RadarState, model: RadarViewModel, assets: List<DirectoryAsset>, explore: () -> Unit) {
    if (assets.isEmpty()) { ResearchChooseFirst(explore); return }
    var ticker by rememberSaveable { mutableStateOf(assets.first().symbol) }
    val selected = ticker.takeIf { t -> assets.any { it.symbol == t } } ?: assets.first().symbol
    CollectionChips(assets.map { it.symbol }, selected, "research-thesis-asset") { ticker = it }
    key(selected) {
        val thesis = state.researchTheses[selected] ?: InvestmentThesis(selected)
        var rationale by rememberSaveable { mutableStateOf(thesis.rationale) }
        var risks by rememberSaveable { mutableStateOf(thesis.risks) }
        var horizon by rememberSaveable { mutableStateOf(thesis.horizon) }
        var review by rememberSaveable { mutableStateOf(thesis.review) }
        var feedback by rememberSaveable { mutableStateOf<String?>(null) }
        ResearchCard(title = "A tua tese · $selected") {
            Text("Guarda o motivo da escolha e o que te faria reavaliar. Horizonte livre; a data de revisão é um registo, não um alerta agendado.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(rationale, { rationale = it.take(4000); feedback = null }, label = { Text("Porque quero investir?") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("research-thesis-rationale"))
            OutlinedTextField(risks, { risks = it.take(4000); feedback = null }, label = { Text("Riscos e condições para reavaliar") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("research-thesis-risks"))
            OutlinedTextField(horizon, { horizon = it.take(120); feedback = null }, label = { Text("Horizonte · ex.: 20 anos / sem prazo") }, modifier = Modifier.fillMaxWidth().testTag("research-thesis-horizon"))
            OutlinedTextField(review, { review = it.take(10); feedback = null }, label = { Text("Rever em · AAAA-MM-DD (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("research-thesis-review"))
            Button({ feedback = model.saveThesis(InvestmentThesis(selected, rationale, risks, horizon, review)) ?: "Tese guardada no dispositivo." }, Modifier.fillMaxWidth().testTag("research-thesis-save")) { Text("Guardar tese") }
            feedback?.let { Text(it, Modifier.testTag("research-thesis-feedback"), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
@Composable private fun ResearchChooseFirst(explore: () -> Unit) {
    ResearchCard(title = "Começa por escolher ativos") {
        Text("Adiciona até três ativos para comparar, testar pesos na carteira e registar a tua tese.", style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(explore, Modifier.testTag("research-choose-first")) { Text("Explorar catálogo →") }
    }
}

/** A searchable country picker avoids a 110-item anchored popup on small screens. */
@Composable private fun ResearchCountryChoice(choices: List<String>, value: String, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedButton({ query = ""; expanded = true }, Modifier.testTag("research-country")) { Text("País da entidade: $value ▾") }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("País da entidade") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CollectionSearch(query, { query = it }, "research-country-filter", "Procurar país")
            val visible = choices.filter { searchKey(it).contains(searchKey(query)) }
            if (visible.isEmpty()) Text("Sem países com esse nome.", style = MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                visible.forEach { country ->
                    TextButton({ change(country); expanded = false }, Modifier.fillMaxWidth().testTag("research-country-$country"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                        Text((if (country == value) "✓ " else "") + country, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }, confirmButton = { TextButton({ expanded = false }) { Text("Fechar") } })
}
