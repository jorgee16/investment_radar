@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.investmentradar.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal val PlanPalette = listOf(Color(0xFF2476E8), Color(0xFF00A184), Color(0xFFE6AF45), Color(0xFF9171DF), Color(0xFFE27A94), Color(0xFF4AA6BE))
internal fun planWeight(value: String): Double = value.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 100 } ?: 0.0

@Composable internal fun PlanPanel(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
@Composable internal fun PlanEyebrow(text: String) {
    Text(text.uppercase(java.util.Locale.forLanguageTag("pt-PT")), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable internal fun PlanPill(text: String, active: Boolean = false) {
    Surface(shape = RoundedCornerShape(8.dp), color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun PlanStrip(weights: List<Double>, modifier: Modifier = Modifier, colors: List<Color> = PlanPalette) {
    val positive = weights.map { it.takeIf { n -> n.isFinite() && n > 0 } ?: 0.0 }
    if (positive.sum() > 0) Row(modifier.fillMaxWidth().height(7.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        positive.forEachIndexed { index, weight -> if (weight > 0) Box(Modifier.weight(weight.toFloat().coerceAtLeast(.0001f)).fillMaxHeight().background(colors[index % colors.size], RoundedCornerShape(4.dp))) }
    }
}
@Composable internal fun PlanHero(plan: InvestmentPlan, active: Boolean, edit: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f)))).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PlanEyebrow("Estratégia de investimento")
                PlanPill(if (active) "Ativo" else "Em edição", active)
            }
            Text(plan.name, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(money(plan.monthly.replace(',', '.').toDoubleOrNull(), "EUR"), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f))
                Text("/ mês", Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { PlanPill("${plan.years} anos"); PlanPill("${plan.groups.size} ${if (plan.groups.size == 1) "componente" else "componentes"}"); if (plan.preferAccumulating) PlanPill("Acumulação") }
            PlanStrip(plan.groups.map { planWeight(it.weight) })
            TextButton(edit, Modifier.testTag("plan-edit-settings"), contentPadding = PaddingValues(0.dp)) { Text("Ajustar orçamento e preferências  ↗") }
        }
    }
}
@Composable internal fun SavedPlanCard(plan: InvestmentPlan, active: Boolean, open: () -> Unit, duplicate: () -> Unit, delete: () -> Unit) {
    PlanPanel(Modifier.testTag("saved-plan-${plan.id}")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(plan.name, Modifier.weight(1f).padding(end = 8.dp), style = MaterialTheme.typography.titleMedium)
            PlanPill(if (active) "Ativo" else "Rascunho", active)
        }
        Text(money(plan.monthly.replace(',', '.').toDoubleOrNull(), "EUR") + " / mês", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
        PlanStrip(plan.groups.map { planWeight(it.weight) })
        Text("${plan.years} anos  ·  ${plan.groups.size} ${if (plan.groups.size == 1) "componente" else "componentes"}  ·  ${if (plan.mode == "fixed") "Pesos fixos" else "Corrigir desvios"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(open, Modifier.testTag("edit-plan-${plan.id}")) { Text("Abrir plano ↗") }
            TextButton(duplicate, Modifier.testTag("duplicate-plan-${plan.id}")) { Text("Duplicar") }
            TextButton(delete, Modifier.testTag("delete-plan-${plan.id}")) { Text("Eliminar", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
@Composable internal fun PresetTile(preset: PlanPreset, index: Int, modifier: Modifier, select: () -> Unit) {
    val weights = when (preset.key) { "world" -> listOf(100.0); "europe", "reserve" -> listOf(80.0, 20.0); "satellite" -> listOf(90.0, 10.0); else -> emptyList() }
    Surface(onClick = select, modifier = modifier.testTag("preset-${preset.key}"), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = PlanPalette[index % PlanPalette.size].copy(alpha = .12f), shape = RoundedCornerShape(10.dp)) {
                Text(listOf("◎", "◒", "+", "◈", "▥", "✦")[index % 6], Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = PlanPalette[index % PlanPalette.size], fontSize = 22.sp)
            }
            Text(preset.name, style = MaterialTheme.typography.titleMedium)
            if (weights.isNotEmpty()) PlanStrip(weights) else PlanPill("À tua medida")
            Text(preset.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Personalizar  ↗", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable internal fun PlanGroupHeading(group: PlanGroup, index: Int, amount: Double?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = PlanPalette[index % PlanPalette.size].copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
            Text((index + 1).toString().padStart(2, '0'), Modifier.padding(12.dp), color = PlanPalette[index % PlanPalette.size], style = MaterialTheme.typography.labelLarge)
        }
        Column(Modifier.weight(1f)) { Text(group.name, style = MaterialTheme.typography.titleMedium); Text(if (group.kind == "reserve") "Reserva em euros" else "${group.items.size} ${if (group.items.size == 1) "instrumento" else "instrumentos"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(horizontalAlignment = Alignment.End) {
            Text(decimal(group.weight.replace(',', '.').toDoubleOrNull(), 1) + "%", style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
            Text(money(amount, "EUR") + "/mês", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun ContributionRow(row: JSONObject, index: Int, mode: String) {
    val percent = row.number("targetPct")
    val value = row.number(if (mode == "fixed") "fixedCents" else "rebalanceCents")?.div(100)
    Column(Modifier.testTag("allocation-row-${row.text("id")}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(9.dp).background(PlanPalette[index % PlanPalette.size], RoundedCornerShape(3.dp)))
            Column(Modifier.weight(1f)) {
                Text(row.text("symbol").ifBlank { row.text("groupName") }, style = MaterialTheme.typography.titleSmall)
                Text(row.text("name"), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(value, "EUR"), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                Text("Objetivo ${decimal(percent, 1)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LinearProgressIndicator(progress = { ((percent ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp), color = PlanPalette[index % PlanPalette.size], trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}
