package com.investmentradar.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun WatchlistScreen(state: RadarState, model: RadarViewModel, add: () -> Unit) {
    ModeBanner(state, model)
    Button(add, Modifier.testTag("add-watch")) { Text("Adicionar ativo +") }
    FinanceCard(title = "Os teus ativos · ${state.watch.size}") {
        if (state.watch.isEmpty()) EmptyState("Watchlist vazia", "Adiciona um ticker para começares a acompanhar um ativo.")
        state.watch.forEach { selected -> PriceRow(selected, state.quotes[selected], { model.selectAsset(selected) }, { model.removeWatch(selected) }); HorizontalDivider() }
    }
}
@Composable internal fun PortfolioScreen(state: RadarState, model: RadarViewModel, add: () -> Unit) {
    var planTab by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!planTab, { planTab = false }, label = { Text("Investimentos") }, modifier = Modifier.testTag("portfolio-holdings-tab"))
        FilterChip(planTab, { planTab = true }, label = { Text("Plano mensal") }, modifier = Modifier.testTag("portfolio-plan-tab"))
    }
    if (planTab) { InvestmentPlanScreen(model); return }
    ModeBanner(state, model)
    Button(add, Modifier.testTag("add-position")) { Text("Adicionar investimento +") }
    val portfolioFocus = LocalFocusManager.current
    val portfolioKeyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var sector by rememberSaveable { mutableStateOf("Todos") }
    fun metadata(position: Position): DirectoryAsset {
        val known = directoryAsset(position.symbol)
        val quote = state.quotes[position.symbol]
        return DirectoryAsset(position.symbol, known?.name ?: quote?.text("name").orEmpty(), known?.sector ?: "Sem setor", position.currency)
    }
    val visible = state.positions.filter { metadata(it).matches(query, sector) }
    val totals = portfolioTotals(state.positions, state.quotes)
    if (totals.isEmpty()) FinanceCard { EmptyState("Ainda não tens investimentos", "Adiciona uma compra com quantidade, preço médio e moeda para acompanhar a carteira.") }
    totals.forEach { total -> FinanceCard(title = "Carteira em ${total.currency}", modifier = Modifier.testTag("total-${total.currency}")) {
        Text(money(total.value, total.currency), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        MetricRow("Investido", money(total.invested, total.currency), modifier = Modifier.testTag("invested-${total.currency}"))
        MetricRow("Resultado total", money(total.value?.minus(total.invested), total.currency), modifier = Modifier.testTag("profit-${total.currency}"))
        if (total.value == null) Text("Faltam cotações na moeda da posição. Os valores não são convertidos automaticamente.", style = MaterialTheme.typography.bodySmall)
        Text("Distribuição do capital investido", style = MaterialTheme.typography.labelLarge)
        state.positions.filter { it.currency == total.currency }.groupBy { it.symbol }.entries
            .sortedByDescending { (_, lots) -> lots.sumOf { it.qty * it.avg } }.forEach { (symbol, lots) ->
                val weight = lots.sumOf { it.qty * it.avg } / total.invested
                Row(Modifier.fillMaxWidth().clickable { model.selectAsset(symbol) }) {
                    Text(symbol, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("${decimal(weight * 100, 1)}%", style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(progress = { weight.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
    } }
    if (state.positions.isNotEmpty()) FinanceCard(title = "Os meus investimentos") {
        OutlinedTextField(query, { query = it }, label = { Text("Pesquisar nome ou símbolo") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { portfolioFocus.clearFocus(); portfolioKeyboard?.hide() }),
            leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().testTag("portfolio-search"))
        SectorMenu(state.positions.map { metadata(it).sector }, sector, "portfolio-sector") { sector = it }
        Text("${visible.size} de ${state.positions.size} compras · totais da carteira completa", style = MaterialTheme.typography.bodySmall)
        if (query.isNotEmpty() || sector != "Todos") TextButton({ query = ""; sector = "Todos" }, Modifier.testTag("portfolio-clear")) { Text("Limpar filtros") }
        if (visible.isEmpty()) EmptyState("Sem correspondências", "Experimenta outro nome ou limpa os filtros.")
    }
    visible.forEach { position ->
        val quote = state.quotes[position.symbol]; val price = quote?.number("price")?.takeIf { quote.text("currency") == position.currency }
        FinanceCard(modifier = Modifier.testTag("position-${position.id}")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { model.selectAsset(position.symbol) }) {
                    Text(position.symbol, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    val asset = metadata(position)
                    if (asset.name.isNotBlank()) Text(asset.name, style = MaterialTheme.typography.bodyMedium)
                    Text(asset.sector, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text("${decimal(position.qty, 4)} unidades", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
@Composable internal fun ScreenerScreen(state: RadarState, model: RadarViewModel) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var minimum by rememberSaveable { mutableFloatStateOf(0f) }; var filter by rememberSaveable { mutableStateOf("") }
    ModeBanner(state, model)
    FinanceCard(title = "Comparar a tua watchlist") {
        Text("Analisa os ativos guardados com o mesmo modelo. Em Live, cada análise pode consumir pedidos do teu plano.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(model::screenWatch, Modifier.testTag("run-screener"), enabled = !state.screeningBusy && state.watch.isNotEmpty()) { Text(if (state.screeningBusy) "A analisar…" else "Analisar ativos") }
        OutlinedTextField(filter, { filter = it }, label = { Text("Filtrar ticker ou empresa") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().testTag("screener-filter"))
        Text("Score mínimo: ${minimum.toInt()}")
        Slider(minimum, { minimum = it }, valueRange = 0f..100f, steps = 19, modifier = Modifier.testTag("screener-minimum"))
        if (state.screeningBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    val results = state.screening.values.filter { s ->
        (minimum == 0f || (s.number("score") ?: -1.0) >= minimum) && (s.text("symbol").contains(filter, true) || s.text("name").contains(filter, true))
    }.sortedByDescending { it.number("score") ?: -1.0 }
    if (results.isEmpty()) FinanceCard { EmptyState("Sem resultados", if (state.screening.isEmpty()) "Seleciona Analisar ativos para executar o screener." else "Nenhum ativo corresponde aos filtros atuais.") }
    results.forEach { s -> FinanceCard(modifier = Modifier.testTag("screened-${s.text("symbol")}")) {
        PriceRow(s.text("symbol"), s, { model.selectAsset(s.text("symbol")) })
        MetricRow("Score", decimal(s.number("score"), 0)); MetricRow("Cobertura", decimal(s.number("coverage"), 0) + "%")
    } }
}
@Composable internal fun CalendarScreen(state: RadarState, model: RadarViewModel) {
    ModeBanner(state, model)
    Text("Resultados · próximos 90 dias", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    EventsList(state.events, state.auxiliaryLoading, model::selectAsset)
}
@Composable internal fun SettingsScreen(state: RadarState, model: RadarViewModel) {
    val context = LocalContext.current
    var licenses by remember { mutableStateOf(false) }
    val license = remember(context) { context.resources.openRawResource(R.raw.manrope_license).bufferedReader().use { it.readText() } }
    val version = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    FinanceCard(title = "Apresentação") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Tema escuro", fontWeight = FontWeight.SemiBold); Text("O tema claro segue as tuas referências.", style = MaterialTheme.typography.bodySmall) }
            Switch(state.dark, model::setDark, Modifier.testTag("dark-theme"))
        }
    }
    FinanceCard(title = "Atualização de cotações") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Atualizar favoritos automaticamente", Modifier.weight(1f))
            Switch(state.pollingEnabled, { model.savePolling(enabled = it) }, Modifier.testTag("polling-enabled"))
        }
        Text("Atualiza cotações dos favoritos e da carteira ao abrir e enquanto a aplicação está ativa. Fundamentais e histórico são carregados à parte.", style = MaterialTheme.typography.bodySmall)
        var intervalMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton({ intervalMenu=true }, Modifier.testTag("polling-interval")) { Text("Intervalo: ${state.pollingMinutes} minutos") }
            DropdownMenu(intervalMenu, { intervalMenu=false }) {
                listOf(5,15,30,60).forEach { minutes -> DropdownMenuItem(text = { Text("$minutes minutos") }, onClick = { intervalMenu=false; model.savePolling(minutes=minutes) }, modifier = Modifier.testTag("polling-interval-$minutes")) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Atualizar com a aplicação fechada", Modifier.weight(1f))
            Switch(state.pollingBackground, { model.savePolling(background=it) }, Modifier.testTag("polling-background"), enabled=state.pollingEnabled)
        }
        Text("Opcional: o Android tenta atualizar de hora a hora, com rede e bateria suficiente. Pode adiar os pedidos; não é tempo real.", style = MaterialTheme.typography.bodySmall)
        var budgetMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton({ budgetMenu=true }, Modifier.testTag("polling-budget")) { Text("Limite automático: ${state.pollingDailyLimit} pedidos/dia") }
            DropdownMenu(budgetMenu, { budgetMenu=false }) {
                listOf(20,50,100,240,500,1000).forEach { limit -> DropdownMenuItem(text = { Text("$limit pedidos/dia") }, onClick = { budgetMenu=false; model.savePolling(dailyLimit=limit) }, modifier = Modifier.testTag("polling-budget-$limit")) }
            }
        }
        Text("O limite conta os pedidos automáticos de cotação, incluindo tentativas de fallback, em todos os providers neste dispositivo (dia UTC). Não substitui as quotas do teu plano; testes e carregamentos manuais usam pedidos adicionais.", style = MaterialTheme.typography.bodySmall)
        Text("Se o provider recusar pedidos, a aplicação faz uma pausa e mantém o último valor com a respetiva data.", style = MaterialTheme.typography.bodySmall)
    }
    FinanceCard(title = "Dados e privacidade") {
        Text(if (state.demo) "Modo atual: Demo" else "Modo atual: Live")
        TextButton({ model.navigate(Page.Providers) }, Modifier.testTag("settings-providers")) { Text("Gerir chaves e modo de dados →") }
        Text("As chaves são cifradas com Android Keystore. A carteira e a watchlist ficam no dispositivo. Os backups da aplicação estão desativados.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FinanceCard(title = "Investment Radar · $version") {
        Text("Acompanha mercados e investimentos, com fontes identificadas e uma carteira guardada neste dispositivo.")
        TextButton({ licenses = true }) { Text("Licença da tipografia · Manrope") }
        Text("Score e valuation são modelos heurísticos para investigação, não aconselhamento financeiro. Confirma sempre a fonte, a moeda e a data dos dados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (licenses) AlertDialog(onDismissRequest = { licenses = false }, title = { Text("Manrope · SIL Open Font License") }, text = { Text(license, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) }, confirmButton = { TextButton({ licenses = false }) { Text("Fechar") } })
}
