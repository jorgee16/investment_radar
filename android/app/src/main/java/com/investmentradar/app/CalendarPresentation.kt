package com.investmentradar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun CalendarScreen(state: RadarState, model: RadarViewModel) {
    val today = LocalDate.now(ZoneOffset.UTC)
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf("90 dias") }
    var universe by rememberSaveable { mutableStateOf("Todos") }
    var query by rememberSaveable { mutableStateOf("") }
    val month = YearMonth.parse(monthText)
    val directory = knownAssets(state).associateBy { it.symbol }
    val knownDates = state.events.mapNotNull { row -> runCatching { LocalDate.parse(row.text("date").take(10)) }.getOrNull()?.let { it to row } }
    val base = knownDates.filter { (_, row) ->
        val ticker = row.text("symbol")
        (universe == "Todos" || universe == "Favoritos" && ticker in state.watch || universe == "Carteira" && state.positions.any { it.symbol == ticker }) &&
            (directory[ticker]?.matches(query) ?: searchKey(ticker).contains(searchKey(query)))
    }
    val counts = base.groupingBy { it.first }.eachCount()
    val visible = base.filter { (date, _) ->
        if (selectedDate != null) date.toString() == selectedDate else date >= today && when (period) {
            "7 dias" -> date < today.plusDays(7)
            "Este mês" -> YearMonth.from(date) == month
            else -> date <= today.plusDays(90)
        }
    }.sortedWith(compareBy({ it.first }, { it.second.text("symbol") }))
    ModeBanner(state, model)
    if (state.auxiliaryLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text("Agenda de resultados", style = MaterialTheme.typography.headlineSmall)
    CollectionChips(listOf("Todos", "Favoritos", "Carteira"), universe, "calendar-universe") { universe = it }
    CollectionSearch(query, { query = it }, "calendar-search")
    FinanceCard(modifier = Modifier.testTag("calendar-month")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ monthText = month.minusMonths(1).toString(); selectedDate = null }, enabled = month > YearMonth.from(today.minusDays(7)), modifier = Modifier.testTag("calendar-previous")) { Text("‹") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-PT"))).replaceFirstChar { it.uppercase() }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton({ monthText = month.plusMonths(1).toString(); selectedDate = null }, enabled = month < YearMonth.from(today.plusDays(90)), modifier = Modifier.testTag("calendar-next")) { Text("›") }
        }
        Row(Modifier.fillMaxWidth()) { listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { day -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        val padding = month.atDay(1).dayOfWeek.value - 1
        val cells = ((padding + month.lengthOfMonth() + 6) / 7) * 7
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (0 until cells).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { cell ->
                    val day = cell - padding + 1
                    if (day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(44.dp)) else {
                        val date = month.atDay(day); val count = counts[date] ?: 0
                        val enabled = date >= today.minusDays(7) && date <= today.plusDays(90)
                        val chosen = selectedDate == date.toString()
                        Surface(Modifier.weight(1f).heightIn(min = 44.dp).testTag("calendar-day-$date").semantics { contentDescription = "$date · $count eventos" }.clickable(enabled = enabled) { selectedDate = if (chosen) null else date.toString() },
                            shape = RoundedCornerShape(10.dp), color = if (chosen) MaterialTheme.colorScheme.primaryContainer else if (date == today) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent) {
                            Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(day.toString(), style = MaterialTheme.typography.labelLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
                                Box(Modifier.size(5.dp).background(if (count > 0) MaterialTheme.colorScheme.secondary else Color.Transparent, CircleShape))
                            }
                        }
                    }
                }
            }
        }
        }
        Text("● Datas com eventos recebidos · cobertura até ${today.plusDays(90)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    CollectionChips(listOf("7 dias", "Este mês", "90 dias"), period, "calendar-period") { period = it; selectedDate = null }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(selectedDate ?: "${visible.size} eventos", Modifier.weight(1f).testTag("calendar-selection"), style = MaterialTheme.typography.titleMedium)
        if (selectedDate != null || query.isNotBlank() || universe != "Todos") TextButton({ selectedDate = null; query = ""; universe = "Todos"; period = "90 dias" }, Modifier.testTag("calendar-clear")) { Text("Limpar filtros") }
    }
    if (!state.auxiliaryLoading && visible.isEmpty()) FinanceCard { EmptyState("Sem eventos neste período", "Altera a data ou os filtros. A disponibilidade do calendário depende do provider e do plano.") }
    visible.groupBy { it.first }.forEach { (date, events) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text(date.dayOfMonth.toString(), Modifier.padding(12.dp), style = MaterialTheme.typography.titleLarge) }
            Column { Text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("pt-PT"))), style = MaterialTheme.typography.titleMedium); Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("pt-PT"))), style = MaterialTheme.typography.labelSmall) }
        }
        events.forEach { (_, row) ->
            val ticker = row.text("symbol")
            FinanceCard(modifier = Modifier.testTag("calendar-event-$ticker-$date")) {
                Column(Modifier.fillMaxWidth().clickable { model.selectAsset(ticker) }) {
                    Text(directory[ticker]?.name ?: ticker, style = MaterialTheme.typography.titleMedium)
                    Text(ticker, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FinancialStat("EPS estimado", decimal(row.number("epsEstimated")), Modifier.weight(1f))
                    FinancialStat("EPS realizado", decimal(row.number("epsActual")), Modifier.weight(1f))
                }
                if (row.text("note").isNotBlank()) Text(row.text("note"), style = MaterialTheme.typography.bodySmall)
                Text("${row.text("source")} · ${row.text("date")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ model.selectAsset(ticker) }, Modifier.testTag("calendar-open-$ticker-$date")) { Text("Abrir ativo →") }
            }
        }
    }
}
