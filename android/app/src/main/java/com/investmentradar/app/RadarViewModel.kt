package com.investmentradar.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal enum class Page(val label: String) {
    Home("Página inicial"), Markets("Mercados"), Portfolio("Carteira"), Watchlist("Watchlist"),
    Research("Descobrir"), Screener("Screener"), Calendar("Calendário"), Providers("Providers"), Settings("Definições")
}
internal data class RadarState(
    val discovered: List<DirectoryAsset> = emptyList(),
    val researchSelection: List<String> = emptyList(), val researchSnapshots: Map<String, JSONObject> = emptyMap(),
    val researchPlanRequested: Boolean = false, val researchBusy: Boolean = false, val researchMessage: String? = null,
    val researchTheses: Map<String, InvestmentThesis> = emptyMap(),
    val ready: Boolean = false, val page: Page = Page.Home, val symbol: String = "AIR.PA", val tab: Int = 0,
    val demo: Boolean = true, val dark: Boolean = false, val watch: List<String> = defaultWatch,
    val positions: List<Position> = emptyList(), val quotes: Map<String, JSONObject> = emptyMap(),
    val snapshot: JSONObject? = null, val history: List<JSONObject> = emptyList(), val days: Int = 90,
    val events: List<JSONObject> = emptyList(), val news: List<JSONObject> = emptyList(),
    val providers: List<JSONObject> = emptyList(), val configured: Set<String> = emptySet(),
    val routing: Map<String, String> = capabilities.keys.associateWith { "auto" },
    val reports: List<JSONObject> = emptyList(), val screening: Map<String, JSONObject> = emptyMap(),
    val loading: Boolean = false, val historyLoading: Boolean = false, val historyMessage: String? = null, val auxiliaryLoading: Boolean = false,
    val saving: Boolean = false, val testing: Boolean = false, val screeningBusy: Boolean = false,
    val error: String? = null, val notice: String? = null,
    val pollingEnabled: Boolean = true, val pollingMinutes: Int = 15, val pollingBackground: Boolean = false,
    val pollingDailyLimit: Int = 10, val pollingActive: Boolean = false, val pollingMessage: String? = null
)

internal class RadarViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = ResearchRepository(application)
    internal val local = LocalPortfolio(application)
    private val prefs = application.getSharedPreferences("radar_appearance", 0)
    private val assetMetadata = AssetMetadataStore(application)
    private val polling = QuotePollingSettings(application)
    private val workspace = ResearchWorkspaceStore(application)
    private val mutable = MutableStateFlow(RadarState(discovered = assetMetadata.load(),
        researchSelection = workspace.selection(), researchTheses = workspace.theses(),
        page = runCatching { Page.valueOf(saved.get<String>("page") ?: "Home") }.getOrDefault(Page.Home),
        symbol = saved.get<String>("symbol")?.takeIf(::validSymbol) ?: "AIR.PA",
        tab = (saved.get<Int>("tab") ?: 0).coerceIn(0,5), dark = prefs.getBoolean("dark", false),
        watch = local.watch(), positions = local.positions(), pollingEnabled = polling.enabled,
        pollingMinutes = polling.intervalMinutes, pollingBackground = polling.background, pollingDailyLimit = polling.dailyLimit
    ))
    val state = mutable.asStateFlow()
    private val investmentPlans = InvestmentPlanStore(application)
    internal fun investmentPlanLibrary() = investmentPlans.load()
    internal suspend fun allocateInvestmentPlan(plan: InvestmentPlan): JSONObject {
        val snapshot = state.value
        return repository.request("/api/plans/allocate", JSONObject().put("plan", plan.json())
            .put("now", System.currentTimeMillis() / 1000).put("demo", snapshot.demo)
            .put("positions", JSONArray(snapshot.positions.map { it.json() })).put("quotes", JSONObject(snapshot.quotes)).toString()) as JSONObject
    }
    internal suspend fun equalPlanWeights(count: Int): List<String> {
        val values = repository.request("/api/plans/equalize", count.toString()) as JSONArray
        return (0 until values.length()).map { values.getDouble(it).toString() }
    }
    internal suspend fun saveInvestmentPlan(plan: InvestmentPlan, activate: Boolean): PlanLibrary {
        val validation = allocateInvestmentPlan(plan)
        check(!activate || validation.optBoolean("valid")) { "Completa os instrumentos e os pesos antes de ativar." }
        check(!activate || plan.mode != "rebalance" || validation.optBoolean("rebalanceAvailable")) { "Faltam dados para corrigir desvios. Escolhe repartição fixa ou revê as associações." }
        val library = investmentPlans.load()
        val active = if (activate) plan.id else if (library.activeId == plan.id) null else library.activeId
        return PlanLibrary(library.plans.filterNot { it.id == plan.id } + plan, active).also(investmentPlans::write)
    }
    internal fun deleteInvestmentPlan(id: String): PlanLibrary {
        val library = investmentPlans.load()
        return PlanLibrary(library.plans.filterNot { it.id == id }, library.activeId?.takeUnless { it == id }).also(investmentPlans::write)
    }
    internal suspend fun planInstrumentMetrics(symbol: String): JSONObject =
        assetCache[symbol] ?: state.value.screening[symbol] ?: repository.request("/api/snapshot", symbol) as JSONObject
    internal suspend fun calculatePlan(input: JSONObject): JSONObject = repository.request("/api/plan", input.toString()) as JSONObject
    internal fun toggleResearch(asset: DirectoryAsset) {
        val current = state.value.researchSelection
        if (asset.symbol !in current && current.size == 3) {
            mutable.update { it.copy(researchMessage = "Compara até 3 ativos. Remove um antes de adicionar outro.") }; return
        }
        rememberAsset(asset)
        val next = if (asset.symbol in current) current - asset.symbol else current + asset.symbol
        workspace.saveSelection(next)
        mutable.update { it.copy(researchSelection = next, researchMessage = null) }
    }
    internal fun openResearchReference(asset: DirectoryAsset) {
        rememberAsset(asset)
        val changed = state.value.symbol != asset.symbol
        if (changed) { assetJob?.cancel(); historyJob?.cancel(); auxiliaryJob?.cancel() }
        saved["symbol"] = asset.symbol; saved["tab"] = 5
        mutable.update { it.copy(symbol = asset.symbol, tab = 5,
            snapshot = it.researchSnapshots[asset.symbol] ?: if (changed) null else it.snapshot,
            history = if (changed) emptyList() else it.history, loading = false, historyLoading = false,
            historyMessage = null, auxiliaryLoading = false, news = if (changed) emptyList() else it.news,
            error = null) }
        navigate(Page.Markets)
    }
    internal fun openResearch(asset: DirectoryAsset? = null) {
        if (asset != null && asset.symbol !in state.value.researchSelection) toggleResearch(asset)
        navigate(Page.Research)
    }
    internal fun saveThesis(thesis: InvestmentThesis): String? {
        val error = workspace.save(thesis)
        if (error == null) mutable.update { it.copy(researchTheses = workspace.theses()) }
        return error
    }
    private var researchJob: Job? = null
    internal fun loadResearchMetrics() {
        if (state.value.researchBusy) return
        val selected = state.value.researchSelection.toList()
        if (selected.isEmpty()) return
        val epoch = generation
        mutable.update { it.copy(researchBusy = true, researchMessage = null) }
        researchJob = viewModelScope.launch {
            try {
                for (ticker in selected) {
                    try {
                        val snapshot = repository.request("/api/snapshot", JSONObject().put("symbol", ticker).put("includeHistory", false).toString()) as JSONObject
                        if (generation == epoch) mutable.update { it.copy(researchSnapshots = it.researchSnapshots + (ticker to snapshot)) }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { if (generation == epoch) mutable.update { it.copy(researchMessage = "Alguns dados não puderam ser atualizados. Os valores anteriores mantêm a sua data.") } }
                }
            } finally { if (generation == epoch) mutable.update { it.copy(researchBusy = false) } }
        }
    }
    internal suspend fun createResearchPlan(amount: String) {
        val selected = state.value.researchSelection.map { assetMetadata(state.value, it) }
        check(selected.isNotEmpty()) { "Seleciona pelo menos um ativo." }
        val weights = equalPlanWeights(selected.size)
        val plan = InvestmentPlan(name = "Em estudo · ${selected.size} ativos", monthly = amount, years = "",
            groups = selected.mapIndexed { index, asset -> PlanGroup(name = asset.name, weight = weights[index],
                items = listOf(PlanInstrument(symbol = asset.symbol, name = asset.name, currency = asset.currency, isin = asset.isin))) })
        saveInvestmentPlan(plan, activate = false)
        navigate(Page.Portfolio)
        mutable.update { it.copy(researchPlanRequested = true) }
    }
    internal fun consumeResearchPlanRequest() { mutable.update { it.copy(researchPlanRequested = false) } }
    internal suspend fun researchImpact(ticker: String, amount: String): JSONObject {
        val current = state.value
        return repository.request("/api/research/impact", JSONObject().put("symbol", ticker).put("amount", amount)
            .put("mode", if (current.demo) "demo" else "live")
            .put("positions", JSONArray(current.positions.map { it.json() })).put("quotes", JSONObject(current.quotes)).toString()) as JSONObject
    }
    private var generation = 0
    private var assetJob: Job? = null
    private var historyJob: Job? = null
    private var auxiliaryJob: Job? = null
    private var quotesJob: Job? = null
    private var screeningJob: Job? = null
    private var pollingJob: Job? = null
    private var foreground = false
    private var quotesPending = false
    private var forcePending = false
    private val assetCache = mutableMapOf<String, JSONObject>()
    private val historyCache = linkedMapOf<Pair<String, Int>, List<JSONObject>>()
    private fun historyFailure(issues: JSONArray?): String = issues?.objects().orEmpty()
        .map { "${it.text("provider")}: ${it.text("message")}" }.distinct().take(3).joinToString("\n")
        .ifBlank { "Não foram recebidos dados históricos para este período. Tenta atualizar mais tarde." }
    private fun acceptHistory(symbol: String, days: Int, rows: List<JSONObject>, issues: JSONArray?) {
        if (rows.isNotEmpty()) {
            historyCache[symbol to days] = rows
            if (historyCache.size > 32) historyCache.remove(historyCache.keys.first())
        }
        val cached = historyCache[symbol to days].orEmpty()
        mutable.update { it.copy(history = rows.ifEmpty { cached }, historyLoading = false,
            historyMessage = if (rows.isNotEmpty()) null else historyFailure(issues) +
                if (cached.isNotEmpty()) "\nA mostrar o histórico guardado deste período; atualização indisponível." else "") }
    }
    private val backStack = ArrayDeque<Page>()
    init { if (local.migrated || !application.getDir("webview", 0).resolve("Default/Local Storage").exists()) completeMigration(JSONObject()) }
    fun completeMigration(raw: JSONObject) {
        try {
            if (!local.migrated) local.migrate(raw)
            mutable.update { it.copy(ready = true, watch = local.watch(), positions = local.positions(), error = null) }
            viewModelScope.launch {
                safe {
                    val config = repository.request("/api/settings") as JSONObject
                    val status = repository.request("/api/status") as JSONObject
                    mutable.update { it.copy(demo = config.optBoolean("demo", true), configured = configured(config), routing = routes(status)) }
                    loadCatalog(); refresh(); updatePolling()
                }
            }
        } catch (_: Exception) { mutable.update { it.copy(error = "Não foi possível migrar os dados guardados. Reabre a aplicação para tentar novamente.") } }
    }
    fun migrationFailed() { mutable.update { it.copy(error = "A migração dos dados antigos não terminou. Reabre a aplicação; os dados originais foram preservados.") } }
    private fun configured(config: JSONObject) = providerKeys.filter { (_, key) -> config.optBoolean(key.removeSuffix("Key") + "Configured") }.keys.toSet()
    private fun routes(status: JSONObject) = capabilities.keys.associateWith { status.optJSONObject("routing")?.optString(it, "auto") ?: "auto" }
    private suspend fun loadCatalog() {
        val providers = (repository.request("/api/providers") as JSONArray).objects().filter { it.text("id") != "demo" }
        mutable.update { it.copy(providers = providers) }
    }
    private suspend fun safe(block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) {
            mutable.update { it.copy(error = "Não foi possível concluir a operação. Verifica a ligação e tenta novamente.") }
        }
    }
    fun navigate(page: Page) {
        if (page == state.value.page) return
        backStack.addLast(state.value.page)
        saved["page"] = page.name
        mutable.update { it.copy(page = page, error = null, notice = null) }
        if (page == Page.Calendar) loadAuxiliary()
    }
    fun back(): Boolean {
        if (state.value.page == Page.Markets && state.value.tab != 0) { setTab(0); return true }
        val target = if (backStack.isNotEmpty()) backStack.removeLast() else if (state.value.page != Page.Home) Page.Home else return false
        saved["page"] = target.name; mutable.update { it.copy(page = target, error = null) }
        return true
    }
    fun selectAsset(raw: String): Boolean {
        val selected = symbol(raw)
        if (!validSymbol(selected)) { mutable.update { it.copy(error = "Ticker inválido. Usa letras, números, ponto ou hífen (ex.: AIR.PA).") }; return false }
        val changed = state.value.symbol != selected
        saved["symbol"] = selected; saved["tab"] = 0
        mutable.update { it.copy(symbol = selected, tab = 0, error = null) }
        navigate(Page.Markets)
        if (changed || state.value.snapshot == null) loadAsset()
        return true
    }
    fun setTab(tab: Int) {
        val needsSnapshot = state.value.tab == 5 && tab in 0..2 && state.value.history.isEmpty()
        saved["tab"] = tab; mutable.update { it.copy(tab = tab) }
        if (needsSnapshot) loadAsset()
        if (tab in 3..4) loadAuxiliary()
    }
    fun dismissMessage() { mutable.update { it.copy(error = null, notice = null) } }
    fun refresh(forceQuotes: Boolean = false) { loadAsset(); loadQuotes(forceQuotes); if (state.value.page == Page.Calendar || state.value.tab in 3..4) loadAuxiliary() }
    fun setForeground(active: Boolean) {
        foreground = active
        if (!active) { pollingJob?.cancel(); pollingJob = null; quotesJob?.cancel(); quotesPending=false; forcePending=false }
        else if (state.value.ready && !state.value.demo) loadQuotes()
        updatePolling()
    }
    private fun updatePolling() {
        val active = foreground && state.value.ready && !state.value.demo && polling.enabled
        mutable.update { it.copy(pollingActive = active) }
        QuotePollingWorker.schedule(getApplication(), state.value.ready && !state.value.demo)
        if (!active) { pollingJob?.cancel(); pollingJob=null; return }
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) { delay(polling.intervalMinutes * 60_000L); loadQuotes() }
        }
    }
    fun savePolling(enabled: Boolean = polling.enabled, minutes: Int = polling.intervalMinutes, background: Boolean = polling.background, dailyLimit: Int = polling.dailyLimit) {
        polling.enabled = enabled; polling.intervalMinutes = minutes; polling.background = background; polling.dailyLimit = dailyLimit
        mutable.update { it.copy(pollingEnabled=enabled, pollingMinutes=minutes, pollingBackground=background, pollingDailyLimit=dailyLimit) }
        pollingJob?.cancel(); pollingJob=null; updatePolling()
        if (foreground && enabled) loadQuotes()
    }
    private fun loadAsset() {
        assetJob?.cancel(); historyJob?.cancel(); auxiliaryJob?.cancel()
        val selected = state.value.symbol; val epoch = generation; val requestedDays = state.value.days
        val cachedHistory = historyCache[selected to requestedDays].orEmpty()
        mutable.update { it.copy(snapshot = assetCache[selected], history = cachedHistory, historyMessage = null, news = emptyList(), events = emptyList(), loading = true, historyLoading = false, auxiliaryLoading = false) }
        assetJob = viewModelScope.launch {
            try {
                val cached = repository.cachedQuotes(listOf(selected))
                if (generation == epoch && state.value.symbol == selected) mutable.update { current ->
                    current.copy(quotes = current.quotes.toMutableMap().apply { cached.forEach { (s, q) -> put(s, mergeQuote(get(s), q)) } })
                }
                val result = repository.request("/api/snapshot", JSONObject().put("symbol", selected).put("days", requestedDays).toString()) as JSONObject
                if (generation == epoch && state.value.symbol == selected) {
                    assetCache[selected] = result
                    mutable.update { it.copy(snapshot = result, loading = false, quotes = it.quotes + (selected to (it.quotes[selected]?.takeIf { q -> q.number("cacheFetchedAt") != null } ?: result))) }
                    if (state.value.days == requestedDays) acceptHistory(selected, requestedDays,
                        result.optJSONArray("history")?.objects().orEmpty(),
                        result.optJSONObject("availability")?.optJSONObject("history")?.optJSONArray("issues"))
                    if (state.value.tab in 3..4) loadAuxiliary()
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (generation == epoch && state.value.symbol == selected) mutable.update { it.copy(loading = false, error = "Não foi possível carregar $selected. Tenta atualizar os dados.") }
            }
        }
    }
    fun setRange(days: Int) {
        historyJob?.cancel(); val selected = state.value.symbol; val epoch = generation
        mutable.update { it.copy(days = days, history = historyCache[selected to days].orEmpty(), historyLoading = true, historyMessage = null) }
        historyJob = viewModelScope.launch {
            try {
                val data = repository.request("/api/history-details", JSONObject().put("symbol", selected).put("days", days).toString()) as JSONObject
                if (generation == epoch && state.value.symbol == selected && state.value.days == days)
                    acceptHistory(selected, days, data.optJSONArray("history")?.objects().orEmpty(), data.optJSONArray("issues"))
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (generation == epoch && state.value.symbol == selected && state.value.days == days)
                    acceptHistory(selected, days, emptyList(), JSONArray().put(JSONObject().put("provider", "Ligação").put("message", "Não foi possível atualizar o histórico. Tenta novamente mais tarde.")))
            }
        }
    }
    private fun loadQuotes(force: Boolean = false) {
        if (quotesJob?.isActive == true) { quotesPending=true; forcePending=forcePending || force; return }
        val epoch = generation
        val symbols = (state.value.watch + state.value.positions.map { it.symbol }).distinct()
        quotesJob = viewModelScope.launch {
            fun publish(values: Map<String, JSONObject>) {
                if (generation == epoch) mutable.update { current ->
                    val updated = current.quotes.toMutableMap()
                    values.forEach { (ticker, quote) -> updated[ticker] = mergeQuote(updated[ticker], quote) }
                    current.copy(quotes = updated)
                }
            }
            try {
                publish(repository.cachedQuotes(symbols))
                if (force || polling.enabled || state.value.demo) {
                    val result = repository.refreshQuotes(symbols, force)
                    publish(result.quotes)
                    if (generation == epoch) mutable.update { it.copy(pollingMessage = if (result.budgetReached) "Limite diário automático atingido. Os últimos valores continuam em cache." else null) }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if (generation == epoch) mutable.update { it.copy(pollingMessage = "Não foi possível atualizar os favoritos. Conservámos os valores em cache.") }
            } finally {
                val ownsJob = generation == epoch && quotesJob == coroutineContext[Job]
                if (ownsJob) quotesJob = null
                if (ownsJob && quotesPending && foreground) {
                    val again = forcePending; quotesPending=false; forcePending=false; loadQuotes(again)
                }
            }
        }
    }
    private fun loadAuxiliary() {
        auxiliaryJob?.cancel(); val epoch = generation; val selected = state.value.symbol; val calendar = state.value.page == Page.Calendar; val tab = state.value.tab
        mutable.update { it.copy(auxiliaryLoading = true, events = if (calendar || tab == 3) emptyList() else it.events, news = if (!calendar && tab != 3) emptyList() else it.news) }
        auxiliaryJob = viewModelScope.launch {
            try {
                if (calendar || tab == 3) {
                    val rows = (repository.request("/api/earnings", if (calendar) "" else selected) as JSONArray).objects()
                    if (generation == epoch && state.value.symbol == selected) mutable.update { it.copy(events = rows, auxiliaryLoading = false) }
                } else {
                    val rows = (repository.request("/api/news", selected) as JSONArray).objects()
                    if (generation == epoch && state.value.symbol == selected) mutable.update { it.copy(news = rows, auxiliaryLoading = false) }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(auxiliaryLoading = false, error = "Dados indisponíveis. Verifica a cobertura do provider.") } }
        }
    }
    internal suspend fun discoverAssets(query: String): JSONObject = repository.request("/api/search", query) as JSONObject
    internal fun rememberAsset(asset: DirectoryAsset) {
        val assets = state.value.discovered.filterNot { it.symbol == asset.symbol } + asset
        assetMetadata.save(assets)
        mutable.update { it.copy(discovered = assets) }
    }
    fun addWatch(raw: String): String? {
        val selected = symbol(raw)
        if (!validSymbol(selected)) return "Ticker inválido. Exemplo: AIR.PA ou AAPL."
        if (selected in state.value.watch) return "Este ativo já está na watchlist."
        return persist(state.value.watch + selected, state.value.positions)
    }
    fun removeWatch(selected: String) { persist(state.value.watch - selected, state.value.positions) }
    fun toggleWatch() { if (state.value.symbol in state.value.watch) removeWatch(state.value.symbol) else addWatch(state.value.symbol) }
    fun addPosition(raw: String, quantity: String, average: String, rawCurrency: String): String? {
        val selected = symbol(raw); val qty = quantity.trim().replace(',', '.').toDoubleOrNull(); val avg = average.trim().replace(',', '.').toDoubleOrNull(); val currency = symbol(rawCurrency)
        if (!validSymbol(selected)) return "Ticker inválido."
        if (qty == null || avg == null || !qty.isFinite() || !avg.isFinite() || qty <= 0 || avg <= 0 || !(qty * avg).isFinite()) return "Introduz quantidade e preço positivos e válidos."
        if (!currency.matches(Regex("[A-Z]{3}"))) return "Usa uma moeda com três letras, por exemplo EUR ou USD."
        return persist(state.value.watch, state.value.positions + Position(UUID.randomUUID().toString(), selected, qty, avg, currency))
    }
    fun removePosition(id: String) { persist(state.value.watch, state.value.positions.filterNot { it.id == id }) }
    private fun persist(watch: List<String>, positions: List<Position>): String? = try {
        local.write(watch, positions); mutable.update { it.copy(watch = watch, positions = positions) }; loadQuotes(); null
    } catch (_: Exception) { "Não foi possível guardar no dispositivo.".also { msg -> mutable.update { it.copy(error = msg) } } }
    fun saveSettings(update: JSONObject) {
        if (state.value.saving) return
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val config = repository.save(update)
                generation++; researchJob?.cancel(); quotesPending=false; forcePending=false; assetCache.clear(); historyCache.clear(); assetJob?.cancel(); historyJob?.cancel(); auxiliaryJob?.cancel(); quotesJob?.cancel(); quotesJob=null; screeningJob?.cancel()
                mutable.update { it.copy(demo = config.optBoolean("demo", true), configured = configured(config), snapshot = null, history = emptyList(), quotes = emptyMap(), screening = emptyMap(), researchSnapshots = emptyMap(), researchBusy = false, researchMessage = null, reports = emptyList(), events = emptyList(), news = emptyList(), screeningBusy = false, testing = false) }
                val status = repository.request("/api/status") as JSONObject
                mutable.update { it.copy(routing = routes(status), saving = false, notice = "Configuração guardada no dispositivo.") }
                loadCatalog(); refresh(); updatePolling()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(saving = false, error = "Não foi possível guardar a configuração. Tenta novamente.") } }
        }
    }
    fun testProviders(raw: String, provider: String = "all"): String? {
        val selected = symbol(raw)
        if (!validSymbol(selected)) return "Ticker de teste inválido."
        if (state.value.testing) return null
        val epoch = generation
        mutable.update { it.copy(testing = true, reports = emptyList(), error = null) }
        viewModelScope.launch {
            try {
                val reports = (repository.request("/api/providers/test", JSONObject().put("symbol", selected).put("provider", provider).toString()) as JSONArray).objects()
                if (generation == epoch) mutable.update { it.copy(reports = reports, testing = false) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { if (generation == epoch) mutable.update { it.copy(testing = false, error = "Não foi possível testar a ligação.") } }
        }
        return null
    }
    fun screenWatch() = screenSymbols(state.value.watch)
    fun screenSymbols(symbols: List<String>) {
        screeningJob?.cancel(); val epoch = generation; val watch = symbols.distinct().filter(::validSymbol)
        mutable.update { it.copy(screeningBusy = true, screening = emptyMap()) }
        screeningJob = viewModelScope.launch {
            try { watch.forEach { selected ->
                val snapshot = repository.request("/api/snapshot", selected) as JSONObject
                if (generation == epoch) mutable.update { it.copy(screening = it.screening + (selected to snapshot)) }
            } } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(error = "Algumas análises não foram carregadas. Podes tentar novamente.") } }
            finally { if (generation == epoch) mutable.update { it.copy(screeningBusy = false) } }
        }
    }
    fun setDark(dark: Boolean) { prefs.edit().putBoolean("dark", dark).apply(); mutable.update { it.copy(dark = dark) } }
    override fun onCleared() { repository.close(); super.onCleared() }
}
