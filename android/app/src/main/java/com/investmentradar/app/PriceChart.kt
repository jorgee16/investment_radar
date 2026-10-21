package com.investmentradar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlin.math.roundToInt

internal fun decimal(value: Double?, digits: Int = 2): String = value?.takeIf { it.isFinite() }?.let {
    java.text.NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("pt-PT")).apply { maximumFractionDigits = digits; minimumFractionDigits = digits }.format(it)
} ?: "—"
internal fun money(value: Double?, currency: String): String = if (value == null) "—" else decimal(value) + if (currency == "EUR") " €" else if (currency.isNotBlank()) " $currency" else " · moeda desconhecida"
internal fun percent(value: Double?): String = if (value == null) "—" else (if (value > 0) "+" else "") + decimal(value) + "%"

@Composable internal fun PriceChart(rows: List<JSONObject>, currency: String, loading: Boolean, candle: Boolean = false, height: Int = 210, emptyMessage: String? = null) {
    val points = remember(rows) { rows.filter { it.number("price") != null } }
    var selection by remember(points) { mutableIntStateOf(-1) }
    val color = if (points.size < 2 || points.last().number("price")!! >= points.first().number("price")!!) FinanceGreen else FinanceRed
    if (loading) { Box(Modifier.fillMaxWidth().height(height.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }; return }
    if (points.isEmpty()) { EmptyState("Histórico indisponível", emptyMessage ?: "Não foram recebidos dados históricos para este período."); return }
    val values = points.flatMap { if (candle) listOfNotNull(it.number("high"), it.number("low")) else listOf(it.number("price")!!) }
    val minimum = values.minOrNull()!!; val maximum = values.maxOrNull()!!
    val spread = (maximum - minimum).takeIf { it > 0 } ?: (kotlin.math.abs(minimum) * .02).coerceAtLeast(1.0)
    val low = minimum - spread * .12; val high = maximum + spread * .12
    val selected = points.getOrNull(selection)
    fun indexAt(x: Float, width: Float): Int = (if (candle) (x / width * points.size).toInt()
        else (x / width * (points.size - 1)).roundToInt()).coerceIn(0, points.lastIndex)
    Column(Modifier.testTag("price-chart")) {
        Text(if (selected == null) "Toca ou arrasta no gráfico para explorar" else "${selected.text("label")} · ${money(selected.number("price"), currency)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth()) {
            val grid = MaterialTheme.colorScheme.outlineVariant
            Canvas(Modifier.weight(1f).height(height.dp).clipToBounds()
                .semantics { contentDescription = "Gráfico de preços com ${points.size} observações, de ${decimal(minimum)} a ${decimal(maximum)}. ${if (candle) "Velas OHLC" else "Linha"}." }
                .pointerInput(points, candle) { detectTapGestures { p -> selection = indexAt(p.x, size.width.toFloat()) } }
                .pointerInput(points, candle) { detectDragGestures(onDragStart = { p -> selection = indexAt(p.x, size.width.toFloat()) }) { change, _ -> change.consume(); selection = indexAt(change.position.x, size.width.toFloat()) } }
            ) {
                fun x(i: Int) = if (candle) (i + .5f) / points.size * size.width else if (points.size == 1) size.width / 2 else i.toFloat() / (points.size - 1) * size.width
                fun y(v: Double) = (size.height * (1 - (v - low) / (high - low))).toFloat()
                for (line in 0..3) { val yy = size.height * line / 3; drawLine(grid, Offset(0f, yy), Offset(size.width, yy), 1f) }
                if (candle) {
                    val width = (size.width / points.size * .65f).coerceIn(1f, 16.dp.toPx())
                    points.forEachIndexed { i, p ->
                        val open = p.number("open") ?: return@forEachIndexed; val close = p.number("close") ?: return@forEachIndexed
                        val ink = if (close >= open) FinanceGreen else FinanceRed
                        drawLine(ink, Offset(x(i), y(p.number("high")!!)), Offset(x(i), y(p.number("low")!!)), 1.5f)
                        drawLine(ink, Offset(x(i), y(open)), Offset(x(i), y(close).let { if (kotlin.math.abs(it - y(open)) < 1) it + 1 else it }), width)
                    }
                } else {
                    val line = Path(); points.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(i), y(p.number("price")!!)) else line.lineTo(x(i), y(p.number("price")!!)) }
                    val fill = Path().apply { addPath(line); lineTo(x(points.lastIndex), size.height); lineTo(x(0), size.height); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .16f), color.copy(alpha = .01f))))
                    drawPath(line, color, style = Stroke(2.dp.toPx()))
                    if (points.size == 1) drawCircle(color, 3.dp.toPx(), Offset(x(0), y(points[0].number("price")!!)))
                }
                if (selected != null) { drawLine(grid, Offset(x(selection), 0f), Offset(x(selection), size.height), 1.5f); drawCircle(color, 4.dp.toPx(), Offset(x(selection), y(selected.number("price")!!))) }
            }
            Column(Modifier.width(64.dp).height(height.dp).padding(start = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(decimal(high), style = MaterialTheme.typography.labelSmall); Text(decimal((high + low) / 2), style = MaterialTheme.typography.labelSmall); Text(decimal(low), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 64.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().text("label"), style = MaterialTheme.typography.labelSmall)
            if (points.size > 1) Text(points.last().text("label"), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ChartControls(state: RadarState, model: RadarViewModel, candle: Boolean, onCandle: (Boolean) -> Unit, onExpand: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(30 to "1M", 90 to "3M", 180 to "6M", 365 to "1A", 1825 to "5A").forEach { (days, label) ->
            FilterChip(state.days == days, { model.setRange(days) }, { Text(label) }, modifier = Modifier.testTag("range-$days"))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!candle, { onCandle(false) }, { Text("Linha") })
            val ohlc = state.history.isNotEmpty() && state.history.all { p -> listOf("open", "high", "low", "close").all { p.number(it) != null } }
            FilterChip(candle && ohlc, { onCandle(true) }, { Text("Velas") }, enabled = ohlc)
        }
        TextButton(onExpand) { Text("Ampliar") }
    }
}
