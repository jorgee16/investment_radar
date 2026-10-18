@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.investmentradar.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

@Composable internal fun RadarApp(model: RadarViewModel, state: RadarState) = FinanceTheme(state.dark) {
    var more by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf(false) }
    var addWatch by rememberSaveable { mutableStateOf(false) }
    var addPosition by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    BackHandler(enabled = state.page != Page.Home || state.tab != 0) { focus.clearFocus(); model.back() }
    Scaffold(modifier = Modifier.fillMaxSize().testTag("native-app").imePadding(), containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.page == Page.Markets) {
                        IconButton({ model.back() }, Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                        Surface(Modifier.weight(1f).testTag("open-search").clickable { search = true }, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(state.symbol, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            }
                        }
                        IconButton({ model.toggleWatch() }, Modifier.testTag("toggle-watch")) { Icon(Icons.Default.Star, if (state.symbol in state.watch) "Remover da watchlist" else "Adicionar à watchlist", tint = if (state.symbol in state.watch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton({
                            val snap = state.snapshot
                            val body = "${snap?.text("name") ?: state.symbol} (${state.symbol}) · ${money(snap?.number("price"), snap?.text("currency").orEmpty())}\nInvestment Radar · ${if (state.demo) "Demonstração: dados sintéticos" else "Dados do provider; confirmar data e fonte"}"
                            runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body) }, "Partilhar ativo")) }
                        }) { Icon(Icons.Default.Share, "Partilhar ativo") }
                    } else {
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(if (state.page == Page.Home) "Investment Radar" else state.page.label, fontWeight = FontWeight.Bold, fontSize = 23.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (state.page == Page.Home) Text("O teu olhar sobre os mercados", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ search = true }, Modifier.testTag("open-search")) { Icon(Icons.Default.Search, "Pesquisar ativo") }
                    }
                    IconButton({ model.refresh() }, Modifier.testTag("refresh"), enabled = state.ready) { Icon(Icons.Default.Refresh, "Atualizar dados") }
                }
                if (state.page == Page.Markets) {
                    ScrollableTabRow(state.tab, edgePadding = 16.dp, containerColor = MaterialTheme.colorScheme.surface, divider = { HorizontalDivider() }) {
                        listOf("Resumo", "Análises", "Dados financeiros", "Resultados", "Notícias").forEachIndexed { index, title ->
                            Tab(state.tab == index, { focus.clearFocus(); model.setTab(index) }, Modifier.testTag("asset-tab-$index"), text = { Text(title, fontWeight = FontWeight.SemiBold) })
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!keyboard) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                val navigationScope = this
                listOf(Page.Home to Icons.Default.Home, Page.Markets to Icons.AutoMirrored.Filled.List, Page.Portfolio to Icons.Default.AccountCircle).forEach { (page, icon) ->
                    NavigationBarItem(selected = state.page == page, onClick = { focus.clearFocus(); model.navigate(page) },
                        icon = { Icon(icon, null) }, label = { Text(if (page == Page.Home) "Início" else page.label, maxLines = 1) }, modifier = Modifier.testTag("nav-${page.name}"))
                }
                Box(Modifier.weight(1f)) {
                    navigationScope.NavigationBarItem(selected = state.page !in listOf(Page.Home, Page.Markets, Page.Portfolio), onClick = { focus.clearFocus(); more = true }, icon = { Icon(Icons.Default.MoreVert, null) }, label = { Text("Mais") }, modifier = Modifier.testTag("nav-More"))
                    DropdownMenu(more, { more = false }) {
                        listOf(Page.Watchlist, Page.Screener, Page.Calendar, Page.Providers, Page.Settings).forEach { page ->
                            DropdownMenuItem(text = { Text(page.label) }, onClick = { more = false; model.navigate(page) }, modifier = Modifier.testTag("menu-${page.name}"))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().testTag("screen-${state.page.name}")) {
            if (!state.ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Text(state.error ?: "A preparar os teus dados…", Modifier.padding(24.dp)) } }
            } else {
                if (state.error != null || state.notice != null) Surface(color = if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.error ?: state.notice.orEmpty(), Modifier.weight(1f).testTag("app-message"), style = MaterialTheme.typography.bodySmall)
                        IconButton(model::dismissMessage) { Icon(Icons.Default.Close, "Fechar mensagem") }
                    }
                }
                key(state.page, if (state.page == Page.Markets) "${state.symbol}-${state.tab}" else "") {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp).widthIn(max = 840.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (state.page) {
                            Page.Home -> HomeScreen(state, model, { addWatch = true })
                            Page.Markets -> AssetScreen(state, model, { addPosition = true })
                            Page.Portfolio -> PortfolioScreen(state, model, { addPosition = true })
                            Page.Watchlist -> WatchlistScreen(state, model, { addWatch = true })
                            Page.Screener -> ScreenerScreen(state, model)
                            Page.Calendar -> CalendarScreen(state, model)
                            Page.Providers -> ProvidersScreen(state, model)
                            Page.Settings -> SettingsScreen(state, model)
                        }
                    }
                }
            }
        }
    }
    if (search) EntryDialog("Pesquisar ativo", "Abrir análise", "search", { search = false }, { raw -> if (model.selectAsset(raw)) { search = false; null } else "Ticker inválido." })
    if (addWatch) EntryDialog("Adicionar à watchlist", "Adicionar", "watch", { addWatch = false }, { raw -> model.addWatch(raw).also { if (it == null) addWatch = false } })
    if (addPosition) PositionDialog(state.symbol, { addPosition = false }) { ticker, qty, avg, currency -> model.addPosition(ticker, qty, avg, currency).also { if (it == null) addPosition = false } }
}

@Composable internal fun FinanceCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (title != null) Text(title.uppercase(java.util.Locale.forLanguageTag("pt-PT")), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
@Composable internal fun EmptyState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun ModeBanner(state: RadarState, model: RadarViewModel) {
    Surface(shape = RoundedCornerShape(12.dp), color = if (state.demo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (state.demo) "DEMO · Dados sintéticos" else "LIVE · Dados dos providers", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("data-mode"))
            Text(if (state.demo) "Demonstração; os valores apresentados não são cotações reais." else "Cotações podem ser diferidas. A cobertura depende do plano de cada provider.", style = MaterialTheme.typography.bodySmall)
            if (!state.demo && state.configured.isEmpty()) TextButton({ model.navigate(Page.Providers) }, contentPadding = PaddingValues(0.dp)) { Text("Configurar chaves de API") }
        }
    }
}
@Composable internal fun PriceRow(ticker: String, quote: JSONObject?, onClick: () -> Unit, remove: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp).testTag("quote-$ticker"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(ticker, fontWeight = FontWeight.Bold)
            Text(quote?.text("name")?.ifBlank { "Dados indisponíveis" } ?: "A carregar…", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(money(quote?.number("price"), quote?.text("currency").orEmpty()), fontWeight = FontWeight.SemiBold)
            val change = quote?.number("changePct")
            Text(percent(change), color = if (change == null) MaterialTheme.colorScheme.onSurfaceVariant else if (change >= 0) FinanceGreen else FinanceRed, style = MaterialTheme.typography.labelMedium)
        }
        if (remove != null) IconButton(remove, Modifier.testTag("remove-watch-$ticker")) { Icon(Icons.Default.Close, "Remover $ticker da watchlist", Modifier.size(20.dp)) }
    }
}
@Composable internal fun HomeScreen(state: RadarState, model: RadarViewModel, addWatch: () -> Unit) {
    ModeBanner(state, model)
    FinanceCard(title = "A minha carteira") {
        val totals = portfolioTotals(state.positions, state.quotes)
        if (totals.isEmpty()) { Text("Tudo começa com a primeira posição", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Acompanha os teus investimentos, o custo médio e a evolução por moeda.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else totals.forEach { total ->
            Text(money(total.value, total.currency), fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Resultado total: ${money(total.value?.minus(total.invested), total.currency)}", color = if ((total.value ?: total.invested) >= total.invested) FinanceGreen else FinanceRed)
        }
        TextButton({ model.navigate(Page.Portfolio) }, Modifier.testTag("open-portfolio")) { Text(if (totals.isEmpty()) "Adicionar um investimento →" else "Ver carteira →") }
    }
    FinanceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("A tua watchlist", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            IconButton(addWatch, Modifier.testTag("add-watch")) { Icon(Icons.Default.Add, "Adicionar ativo") }
        }
        if (state.watch.isEmpty()) EmptyState("Ainda sem ativos", "Adiciona um ticker para acompanhares o mercado.")
        state.watch.forEachIndexed { i, ticker -> PriceRow(ticker, state.quotes[ticker], { model.selectAsset(ticker) }); if (i < state.watch.lastIndex) HorizontalDivider() }
        TextButton({ model.navigate(Page.Watchlist) }) { Text("Gerir watchlist →") }
    }
    FinanceCard(title = "Explorar") {
        TextButton({ model.navigate(Page.Screener) }) { Text("Comparar ativos no screener →") }
        TextButton({ model.navigate(Page.Calendar) }) { Text("Consultar calendário de resultados →") }
        TextButton({ model.navigate(Page.Providers) }) { Text("Configurar providers →") }
    }
}

@Composable internal fun EntryDialog(title: String, action: String, tag: String, dismiss: () -> Unit, submit: (String) -> String?) {
    var value by rememberSaveable { mutableStateOf("") }; var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    fun save() { error = submit(value); if (error == null) focus.clearFocus() }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value, { value = it.take(32); error = null }, label = { Text("Ticker") }, placeholder = { Text("AIR.PA, AAPL…") }, singleLine = true,
                isError = error != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { save() }), modifier = Modifier.fillMaxWidth().testTag("$tag-input"))
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("form-error"))
        }
    }, confirmButton = { TextButton({ save() }, Modifier.testTag("$tag-submit")) { Text(action) } }, dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}
@Composable internal fun PositionDialog(initial: String, dismiss: () -> Unit, submit: (String, String, String, String) -> String?) {
    var ticker by rememberSaveable { mutableStateOf(initial) }; var qty by rememberSaveable { mutableStateOf("") }; var avg by rememberSaveable { mutableStateOf("") }; var currency by rememberSaveable { mutableStateOf("EUR") }; var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Adicionar investimento") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(ticker, { ticker = it.take(32) }, label = { Text("Ticker") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("position-symbol"))
            OutlinedTextField(qty, { qty = it }, label = { Text("Quantidade") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("position-qty"))
            OutlinedTextField(avg, { avg = it }, label = { Text("Preço médio por unidade") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("position-avg"))
            OutlinedTextField(currency, { currency = it.take(3) }, label = { Text("Moeda do preço médio") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("position-currency"))
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("form-error"))
        }
    }, confirmButton = { TextButton({ error = submit(ticker, qty, avg, currency) }, Modifier.testTag("position-submit")) { Text("Guardar") } }, dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}

internal fun openArticle(context: android.content.Context, url: String) {
    val uri = Uri.parse(url)
    if (ProviderPolicy.mayOpenExternal(uri.scheme, uri.host, true, true)) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
