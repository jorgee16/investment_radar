package com.investmentradar.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable internal fun InvestmentPlanScreen(model: RadarViewModel) {
    val state by model.state.collectAsState()
    val initial = remember { runCatching { model.investmentPlanLibrary() } }
    var library by remember { mutableStateOf(initial.getOrDefault(PlanLibrary())) }
    var message by remember { mutableStateOf(initial.exceptionOrNull()?.message) }
    var raw by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf("") }
    var addGroup by rememberSaveable { mutableStateOf(false) }
    var pickGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var projection by rememberSaveable { mutableStateOf(false) }
    var associations by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun change(plan: InvestmentPlan) { raw = plan.json().toString(); report = null; message = null }
    val plan = remember(raw) { raw.takeIf { it.isNotBlank() }?.let { InvestmentPlan.read(JSONObject(it)) } }
    fun leave() {
        val original = library.plans.firstOrNull { it.id == plan?.id }?.json()?.toString()
        if (raw != original) confirmLeave = true else raw = ""
    }
    BackHandler(plan != null && pickGroup == null && !addGroup && !confirmLeave) { focus.clearFocus(); keyboard?.hide(); leave() }
    LaunchedEffect(raw, state.positions, state.quotes) {
        report = null
        if (plan != null) {
            delay(180)
            try { report = model.allocateInvestmentPlan(plan) } catch (e: CancellationException) { throw e } catch (_: Exception) { message = "Não foi possível calcular o plano. Revê os campos." }
        }
    }
    if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("plan-message"))
    if (initial.isFailure) { Text("Não foi possível abrir os planos guardados. Os dados foram preservados."); return }
    if (plan == null) {
        Text("Os meus planos", style = MaterialTheme.typography.headlineSmall)
        Text("Define onde investir e como repartir cada reforço. As compras reais continuam na carteira.", style = MaterialTheme.typography.bodyMedium)
        library.plans.forEach { saved -> FinanceCard(title = saved.name, modifier = Modifier.testTag("saved-plan-${saved.id}")) {
            Text(if (library.activeId == saved.id) "Plano ativo" else "Rascunho / alternativa", color = MaterialTheme.colorScheme.primary)
            Text("${saved.monthly} €/mês · ${saved.groups.size} grupos")
            Row {
                TextButton({ change(saved); selectedGroup = saved.groups.firstOrNull()?.id.orEmpty() }, Modifier.testTag("edit-plan-${saved.id}")) { Text("Abrir") }
                TextButton({ change(saved.copy(id = planId(), name = saved.name + " · cópia")); selectedGroup = saved.groups.firstOrNull()?.id.orEmpty() }, Modifier.testTag("duplicate-plan-${saved.id}")) { Text("Duplicar") }
                TextButton({ deleteId = saved.id }, Modifier.testTag("delete-plan-${saved.id}")) { Text("Eliminar") }
            }
        } }
        FinanceCard(title = "Criar um plano") {
            planPresets.forEach { preset ->
                OutlinedButton({ change(createInvestmentPlan(preset.key)); selectedGroup = "world" }, Modifier.fillMaxWidth().testTag("preset-${preset.key}")) {
                    Column(Modifier.fillMaxWidth()) { Text(preset.name, fontWeight = FontWeight.Bold); Text(preset.description, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Text("Modelos editáveis, não recomendações personalizadas. Mundial, Europa e ações individuais continuam expostos às quedas da bolsa.", style = MaterialTheme.typography.bodySmall)
        }
    } else {
        TextButton({ focus.clearFocus(); leave() }, Modifier.testTag("plan-back")) { Text("← Os meus planos") }
        FinanceCard(title = "Construir o plano") {
            PlanInput("Nome do plano", plan.name, "plan-name", false) { change(plan.copy(name = it.take(80))) }
            PlanInput("Reforço mensal (€)", plan.monthly, "plan-monthly") { change(plan.copy(monthly = it)) }
            PlanInput("Horizonte (anos)", plan.years, "plan-years") { change(plan.copy(years = it)) }
            Row { Checkbox(plan.preferAccumulating, { change(plan.copy(preferAccumulating = it)) }); Text("Preferir ETF acumulativos nas sugestões") }
            Text("${if (plan.mode == "fixed") "Repartição fixa" else "Correção de desvios"} · orçamento em euros", style = MaterialTheme.typography.labelLarge)
        }
        if (plan.groups.isNotEmpty()) FinanceCard(title = "Distribuição planeada") {
            PlanDonut(plan.groups, selectedGroup) { selectedGroup = it }
            Text("Soma dos grupos: ${decimal(report?.number("totalWeight"), 2)}%", modifier = Modifier.testTag("plan-weight-total"))
            Text("Toca numa parcela para editar. A ativação exige uma soma de 100%.", style = MaterialTheme.typography.bodySmall)
        }
        plan.groups.forEach { group ->
            fun update(updated: PlanGroup) = change(plan.copy(groups = plan.groups.map { if (it.id == group.id) updated else it }))
            FinanceCard(title = group.name, modifier = Modifier.testTag("plan-group-${group.id}")) {
                PlanInput("Peso no plano (%)", group.weight, "plan-weight-${group.id}") { update(group.copy(weight = it)) }
                Row {
                    TextButton({ selectedGroup = if (selectedGroup == group.id) "" else group.id }, Modifier.testTag("expand-group-${group.id}")) { Text(if (selectedGroup == group.id) "Recolher" else "Editar componentes") }
                    TextButton({ change(plan.copy(groups = plan.groups.filterNot { it.id == group.id })) }, Modifier.testTag("remove-group-${group.id}")) { Text("Remover grupo") }
                }
                if (selectedGroup == group.id) {
                    Slider(value = group.weight.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f, onValueChange = { update(group.copy(weight = it.toInt().toString())) }, valueRange = 0f..100f, modifier = Modifier.testTag("weight-slider-${group.id}"))
                    if (group.kind != "sector") PlanInput("Nome do grupo", group.name, "group-name-${group.id}", false) { update(group.copy(name = it.take(80))) }
                    if (group.kind == "reserve") {
                        PlanInput("Saldo atual desta reserva (€)", group.balance, "group-balance-${group.id}") { update(group.copy(balance = it)) }
                        Text("Saldo manual usado na correção dos desvios; não está ligado a um banco.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        group.items.forEach { item ->
                            HorizontalDivider(); Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text("${item.symbol} · ${item.currency}", style = MaterialTheme.typography.bodySmall)
                            PlanInput("Peso dentro do grupo (%)", item.weight, "item-weight-${item.id}") { weight -> update(group.copy(items = group.items.map { if (it.id == item.id) it.copy(weight = weight) else it })) }
                            TextButton({ update(group.copy(items = group.items.filterNot { it.id == item.id })) }, Modifier.testTag("remove-item-${item.id}")) { Text("Remover instrumento") }
                        }
                        if (group.items.isEmpty()) Text("Escolhe os instrumentos deste grupo.")
                        Button({ focus.clearFocus(); keyboard?.hide(); pickGroup = group.id }, Modifier.testTag("group-add-${group.id}"), enabled = group.items.size < 50) { Text("Pesquisar ETF ou ações") }
                        if (group.items.size > 1) TextButton({ busy = true; scope.launch { try { val weights = model.equalPlanWeights(group.items.size); update(group.copy(items = group.items.mapIndexed { i, item -> item.copy(weight = weights[i]) })) } finally { busy = false } } }, enabled = !busy, modifier = Modifier.testTag("equal-items-${group.id}")) { Text("Distribuir igualmente no grupo") }
                        Text("Ao adicionar instrumentos, os pesos internos são repartidos igualmente; depois podes ajustá-los.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        OutlinedButton({ addGroup = true }, Modifier.fillMaxWidth().testTag("plan-add-group"), enabled = plan.groups.size < 30) { Text("Adicionar setor, grupo ou reserva +") }
        if (plan.groups.size > 1) TextButton({ busy = true; scope.launch { try { val weights = model.equalPlanWeights(plan.groups.size); change(plan.copy(groups = plan.groups.mapIndexed { i, g -> g.copy(weight = weights[i]) })) } finally { busy = false } } }, enabled = !busy, modifier = Modifier.testTag("plan-equal-groups")) { Text("Repartir igualmente entre grupos") }
        FinanceCard(title = "Como distribuir os reforços") {
            FilterChip(plan.mode == "fixed", { change(plan.copy(mode = "fixed")) }, label = { Text("Percentagens fixas") }, modifier = Modifier.testTag("plan-mode-fixed"))
            FilterChip(plan.mode == "rebalance", { change(plan.copy(mode = "rebalance")) }, label = { Text("Corrigir desvios, sem vender") }, modifier = Modifier.testTag("plan-mode-rebalance"))
            TextButton({ associations = !associations }, Modifier.testTag("plan-associations")) { Text("Associar compras da carteira (${plan.linkedPositionIds.size})") }
            if (associations) {
                Text("Só estas compras contam para corrigir desvios. Confirma todas as posições que queres incluir.", style = MaterialTheme.typography.bodySmall)
                state.positions.forEach { position -> Row {
                    Checkbox(position.id in plan.linkedPositionIds, { checked -> change(plan.copy(linkedPositionIds = if (checked) plan.linkedPositionIds + position.id else plan.linkedPositionIds - position.id)) }, Modifier.testTag("link-position-${position.id}"))
                    Text("${position.symbol} · ${decimal(position.qty, 4)} unidades · ${position.currency}")
                } }
                if (state.positions.isEmpty()) Text("Ainda não há compras registadas. O plano pode começar do zero.")
                if (plan.linkedPositionIds.any { id -> state.positions.none { it.id == id } }) TextButton({ change(plan.copy(linkedPositionIds = plan.linkedPositionIds.intersect(state.positions.map { it.id }.toSet()))) }) { Text("Remover associações a compras eliminadas") }
            }
        }
        report?.let { allocation ->
            val issues = allocation.optJSONArray("issues")?.strings().orEmpty()
            if (issues.isNotEmpty()) FinanceCard(title = "Completar antes de ativar") { issues.forEach { Text(it, modifier = Modifier.testTag("plan-validation")) } }
            if (allocation.optBoolean("valid")) FinanceCard(title = "O próximo reforço") {
                val available = plan.mode == "fixed" || allocation.optBoolean("rebalanceAvailable")
                if (!available) {
                    Text("A correção de desvios precisa de dados completos. A repartição fixa continua disponível.", modifier = Modifier.testTag("plan-rebalance-blocked"))
                    allocation.optJSONArray("rebalanceIssues")?.strings()?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                } else {
                    Text("${money(allocation.number("budgetCents")?.div(100), "EUR")} a distribuir", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("plan-budget"))
                    if (plan.mode == "rebalance" && state.demo) Text("DEMO · correção baseada em cotações sintéticas", color = MaterialTheme.colorScheme.error)
                    allocation.getJSONArray("rows").objects().forEach { row ->
                        MetricRow(row.text("name"), money(row.number(if (plan.mode == "fixed") "fixedCents" else "rebalanceCents")?.div(100), "EUR"), modifier = Modifier.testTag("allocation-row-${row.text("id")}"))
                        Text("${row.text("groupName")} · objetivo ${decimal(row.number("targetPct"), 2)}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Montantes planeados. Não executa ordens nem pressupõe frações, câmbio ou comissões da XTB. Regista cada compra real na carteira.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        Button({ focus.clearFocus(); keyboard?.hide(); busy = true; scope.launch { try { library = model.saveInvestmentPlan(plan, false); message = "Rascunho guardado. Para o usar nos reforços, ativa o plano." } catch (_: Exception) { message = "Não foi possível guardar o plano." } finally { busy = false } } }, Modifier.fillMaxWidth().testTag("plan-save"), enabled = !busy) { Text("Guardar rascunho") }
        Button({ focus.clearFocus(); keyboard?.hide(); busy = true; scope.launch { try { library = model.saveInvestmentPlan(plan, true); message = "Plano ativo guardado." } catch (e: Exception) { message = e.message ?: "Não foi possível ativar." } finally { busy = false } } }, Modifier.fillMaxWidth().testTag("plan-activate"), enabled = !busy && report?.optBoolean("valid") == true && (plan.mode == "fixed" || report?.optBoolean("rebalanceAvailable") == true)) { Text("Guardar e ativar plano") }
        TextButton({ projection = !projection }, Modifier.testTag("plan-projection-toggle")) { Text(if (projection) "Fechar projeção opcional" else "Simular evolução e objetivo (opcional)") }
        if (projection) PlanProjection(plan, model)
        if (addGroup) AddPlanGroupDialog({ addGroup = false }) { group -> change(plan.copy(groups = plan.groups + group.copy(weight = if (plan.groups.isEmpty()) "100" else "0"))); selectedGroup = group.id; addGroup = false }
        val groupToPick = plan.groups.firstOrNull { it.id == pickGroup }
        if (groupToPick != null) PlanAssetPicker(groupToPick, state, model, plan.preferAccumulating, { pickGroup = null }) { asset ->
            val duplicate = plan.groups.flatMap { it.items }.any { it.symbol == asset.symbol || (asset.isin.isNotBlank() && it.isin == asset.isin) }
            if (duplicate) { message = "Este instrumento já está no plano."; pickGroup = null }
            else {
                val next = groupToPick.items + PlanInstrument(symbol = asset.symbol, name = asset.name, currency = asset.currency, isin = asset.isin)
                busy = true
                scope.launch { try { val weights = model.equalPlanWeights(next.size); change(plan.copy(groups = plan.groups.map { if (it.id == groupToPick.id) it.copy(items = next.mapIndexed { i, item -> item.copy(weight = weights[i]) }) else it })); pickGroup = null } catch (_: Exception) { message = "Não foi possível adicionar o instrumento." } finally { busy = false } }
            }
        }
    }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("Sair sem guardar?") }, text = { Text("As alterações deste rascunho ainda não foram guardadas.") }, confirmButton = { TextButton({ confirmLeave = false; raw = "" }, Modifier.testTag("plan-discard")) { Text("Descartar alterações") } }, dismissButton = { TextButton({ confirmLeave = false }) { Text("Continuar a editar") } })
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Eliminar este plano?") }, text = { Text("As compras registadas na carteira são independentes do plano.") }, confirmButton = { TextButton({ try { library = model.deleteInvestmentPlan(deleteId!!) } catch (_: Exception) { message = "Não foi possível eliminar." }; deleteId = null }, Modifier.testTag("confirm-delete-plan")) { Text("Eliminar plano") } }, dismissButton = { TextButton({ deleteId = null }) { Text("Cancelar") } })
}

@Composable internal fun PlanInput(label: String, value: String, tag: String, numeric: Boolean = true, change: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }), modifier = Modifier.fillMaxWidth().testTag(tag))
}

@Composable private fun AddPlanGroupDialog(dismiss: () -> Unit, add: (PlanGroup) -> Unit) {
    var kind by rememberSaveable { mutableStateOf("Setor") }; var name by rememberSaveable { mutableStateOf("") }; var sector by rememberSaveable { mutableStateOf("Tecnologia") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Adicionar componente") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanChoice("Componente", listOf("Setor", "Grupo livre", "Reserva"), kind, "new-group-kind") { kind = it }
            if (kind == "Setor") PlanChoice("Setor", investmentSectors, sector, "new-group-sector") { sector = it }
            else PlanInput("Nome", name, "new-group-name", false) { name = it.take(80) }
        }
    }, confirmButton = { TextButton({ add(PlanGroup(name = if (kind == "Setor") sector else name.trim(), kind = if (kind == "Setor") "sector" else if (kind == "Reserva") "reserve" else "custom")) }, enabled = kind == "Setor" || name.isNotBlank(), modifier = Modifier.testTag("new-group-submit")) { Text("Adicionar") } }, dismissButton = { TextButton(dismiss) { Text("Cancelar") } })
}

@Composable private fun PlanDonut(groups: List<PlanGroup>, selected: String, select: (String) -> Unit) {
    val colors = listOf(Color(0xFF007D68), Color(0xFF2679C7), Color(0xFFF2AA38), Color(0xFF8B63C5), Color(0xFFE16C64), Color(0xFF5E8791))
    val weights = groups.map { it.weight.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 100 } ?: 0.0 }
    val sum = weights.sum()
    Box(contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("plan-allocation-chart").semantics { contentDescription = "Distribuição planeada. Seleciona também pelos botões de grupo abaixo." }.pointerInput(groups) {
        detectTapGestures { point ->
            val x = point.x - size.width / 2; val y = point.y - size.height / 2
            val radius = size.height * .45f
            if (sum > 0 && sqrt(x*x+y*y) in radius * .5f..radius * 1.2f) {
                val angle = ((Math.toDegrees(atan2(y.toDouble(), x.toDouble())) + 450) % 360) / 360 * sum
                var end = 0.0
                groups.indices.firstOrNull { end += weights[it]; angle < end }?.let { select(groups[it].id) }
            }
        }
    }) {
        val diameter = size.height * .8f
        val top = Offset((size.width - diameter)/2, (size.height-diameter)/2)
        var start = -90f
        if (sum <= 0) drawArc(Color.LightGray, 0f, 360f, false, top, Size(diameter, diameter), style = Stroke(24.dp.toPx()))
        else groups.forEachIndexed { i, group -> val sweep = (weights[i] / sum * 360).toFloat(); drawArc(colors[i % colors.size], start, sweep, false, top, Size(diameter, diameter), style = Stroke(if (selected == group.id) 30.dp.toPx() else 24.dp.toPx())); start += sweep }
    }
    val current = groups.firstOrNull { it.id == selected }
    Column(Modifier.widthIn(max = 115.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(decimal(current?.weight?.replace(',', '.')?.toDoubleOrNull() ?: sum, 1) + "%", style = MaterialTheme.typography.headlineSmall)
        Text(current?.name ?: "Plano", style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
    }
    groups.forEach { group -> FilterChip(selected == group.id, { select(group.id) }, label = { Text("${group.name} · ${decimal(group.weight.replace(',', '.').toDoubleOrNull(), 1)}%") }, modifier = Modifier.testTag("select-group-${group.id}")) }
}

@Composable private fun PlanProjection(plan: InvestmentPlan, model: RadarViewModel) {
    var goal by rememberSaveable { mutableStateOf("100000") }; var annual by rememberSaveable { mutableStateOf("5") }; var tax by rememberSaveable { mutableStateOf("28") }
    var result by remember { mutableStateOf<JSONObject?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(plan.monthly, plan.years, goal, annual, tax) {
        result = null; error = null; delay(250)
        try {
            val input = JSONObject().put("monthly", plan.monthly.replace(',', '.').toDouble()).put("years", plan.years.toDouble()).put("goal", goal.replace(',', '.').toDouble()).put("annual", annual.replace(',', '.').toDouble()).put("tax", tax.replace(',', '.').toDouble()).put("equity", 100).put("stocks", 0)
            result = model.calculatePlan(input)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "Confirma objetivo, prazo, retorno (−20 a 20%) e imposto (0 a 50%)." }
    }
    FinanceCard(title = "Projeção opcional · novos reforços") {
        PlanInput("Objetivo líquido (€)", goal, "projection-goal") { goal = it }
        PlanInput("Retorno anual efetivo hipotético (%)", annual, "projection-annual") { annual = it }
        PlanInput("Imposto sobre ganhos (%)", tax, "projection-tax") { tax = it }
        result?.let { r -> val last = r.getJSONArray("points").objects().last(); MetricRow("Investido", money(last.number("invested"), "EUR")); MetricRow("Líquido após venda", money(last.number("net"), "EUR")); MetricRow("Reforço necessário", money(r.number("requiredMonthly"), "EUR") + "/mês") }
        if (error != null) Text(error!!)
        Text("Cenário para a carteira inteira, não previsão. Reforços no fim do mês; exclui posições existentes, inflação, comissões e benefícios fiscais. A tributação futura pode mudar.", style = MaterialTheme.typography.bodySmall)
    }
}
