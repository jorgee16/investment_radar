package com.investmentradar.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class VisualMetric(val key: String, val label: String, val value: String, val source: String = "", val explanation: String = "", val number: Double? = null)
internal fun metricSource(snapshot: JSONObject, key: String): String = snapshot.optJSONObject("metricSources")?.optJSONObject(key)?.let {
    listOf(it.text("source"), it.text("period"), it.text("basis")).filter(String::isNotBlank).joinToString(" · ")
}.orEmpty()
internal fun metricExplanation(key: String) = when (key) {
    "pe" -> "Preço / lucro por ação dos últimos 12 meses. Um múltiplo não é, por si só, uma recomendação."
    "forwardPE" -> "Preço / lucro por ação estimado. Depende das previsões disponíveis."
    "revenueGrowth" -> "Variação da receita. Consulta a fonte para distinguir crescimento anual de trimestral."
    "epsGrowth" -> "Variação do lucro por ação, no período indicado pela fonte."
    "fcfMargin" -> "Fluxo de caixa livre em percentagem da receita."
    "roe" -> "Lucro em relação ao capital próprio. Valores negativos e alavancagem exigem contexto."
    "debtToEquity" -> "Dívida em relação ao capital próprio; apresentado como rácio."
    "earningsSurprise" -> "Diferença percentual entre EPS realizado e estimado. Não é uma revisão de estimativas."
    "estimateRevision" -> "Alteração de uma previsão para o mesmo período fiscal."
    "maxDrawdownPct" -> "Maior queda entre um pico e um fecho posterior dentro do histórico disponível. Não mede perdas intradiárias."
    "sessionVolatilityPct" -> "Desvio padrão amostral das variações percentuais entre fechos consecutivos. Não anualizado."
    "sma20", "sma50" -> "Média aritmética dos últimos 20 ou 50 fechos disponíveis. Sem histórico suficiente o valor permanece desconhecido."
    else -> "Calculado com os fechos do período indicado. Exclui dividendos, comissões e conversões cambiais."
}

@Composable internal fun MetricTiles(metrics: List<VisualMetric>) {
    var detail by remember { mutableStateOf<VisualMetric?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f || maxWidth < 280.dp) 1 else if (maxWidth >= 600.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            metrics.chunked(columns).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { metric ->
                        Surface(Modifier.weight(1f).heightIn(min = 112.dp).testTag("metric-${metric.key}").clickable { detail = metric },
                        shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(metric.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(metric.value, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                Text(metric.source.substringBefore(" · ").ifBlank { "Ver indicador ↗" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                    repeat(columns - pair.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    detail?.let { metric ->
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(metric.label) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(metric.value, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(metric.explanation)
                Text(metric.source.ifBlank { "Fonte ou período não indicados pelo provider." }, style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton({ detail = null }, Modifier.testTag("metric-close")) { Text("Fechar") } })
    }
}

@Composable internal fun HistoricalDashboard(snapshot: JSONObject) {
    val stats = snapshot.optJSONObject("priceStatistics") ?: return
    var drawdown by rememberSaveable(snapshot.text("symbol")) { mutableStateOf(false) }
    val currency = snapshot.text("currency")
    val source = "${stats.text("from")} → ${stats.text("to")} · ${stats.text("source")}"
    FinanceCard(title = "Comportamento do preço", modifier = Modifier.testTag("price-analysis")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(percent(stats.number("changePct")), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = if ((stats.number("changePct") ?: 0.0) >= 0) FinanceGreen else FinanceRed)
                Text("Variação no período", style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${stats.optInt("observations")} fechos", fontWeight = FontWeight.SemiBold)
                Text(stats.text("source"), style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!drawdown, { drawdown = false }, { Text("Evolução") }, Modifier.testTag("analytics-evolution"))
            FilterChip(drawdown, { drawdown = true }, { Text("Quedas") }, Modifier.testTag("analytics-drawdown"))
        }
        val rows = stats.optJSONArray("path")?.objects().orEmpty().map { p ->
            JSONObject().put("label", p.text("label")).put("price", p.number(if (drawdown) "drawdownPct" else "indexed"))
        }
        Text(if (drawdown) "Queda desde o pico anterior (%)" else "Evolução com base 100 no primeiro fecho", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("analytics-chart-mode"))
        PriceChart(rows, if (drawdown) "%" else "base 100", false, height = 150)
        val returns = stats.optJSONArray("returns")?.objects().orEmpty()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            returns.forEach { r -> Column(Modifier.weight(1f)) {
                Text("${r.optInt("sessions")} ${if (r.optInt("sessions") == 1) "sessão" else "sessões"}", style = MaterialTheme.typography.labelSmall)
                Text(percent(r.number("changePct")), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                    color = r.number("changePct")?.let { if (it >= 0) FinanceGreen else FinanceRed } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
        Text(source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FinanceCard(title = "Amplitude e risco") {
        RangeVisual(stats.number("low")!!, stats.number("high")!!, stats.number("last"), currency)
        MetricTiles(listOf("maxDrawdownPct" to "Maior queda", "sessionVolatilityPct" to "Volatilidade / sessão", "sma20" to "Média de 20 fechos", "sma50" to "Média de 50 fechos").map { (key, label) ->
            VisualMetric(key, label, if (key.startsWith("sma")) money(stats.number(key), currency) else if (key == "sessionVolatilityPct") stats.number(key)?.let { decimal(it) + "%" } ?: "—" else percent(stats.number(key)), source, metricExplanation(key), stats.number(key))
        })
        Text("Fechos do histórico disponível · sem dividendos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun RangeVisual(low: Double, high: Double, current: Double?, currency: String) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth()) {
        Text("Último fecho · ${money(current, currency)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Canvas(Modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = "Intervalo de ${money(low, currency)} a ${money(high, currency)}, último fecho ${money(current, currency)}" }) {
            val inset = 7.dp.toPx(); val w = size.width - inset * 2
            drawLine(grid, Offset(inset, size.height / 2), Offset(size.width - inset, size.height / 2), 5.dp.toPx(), StrokeCap.Round)
            if (current != null && high > low) {
                val x = inset + ((current - low) / (high - low)).toFloat().coerceIn(0f, 1f) * w
                drawLine(accent.copy(alpha = .35f), Offset(inset, size.height / 2), Offset(x, size.height / 2), 5.dp.toPx(), StrokeCap.Round)
                drawCircle(accent, 6.dp.toPx(), Offset(x, size.height / 2))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mín. ${money(low, currency)}", style = MaterialTheme.typography.labelSmall)
            Text("Máx. ${money(high, currency)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable internal fun ScoreDashboard(snapshot: JSONObject) {
    val score = snapshot.number("score")
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    FinanceCard(title = "Análise Investment Radar") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    drawArc(grid, 135f, 270f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    if (score != null) drawArc(accent, 135f, (score * 2.7).toFloat().coerceIn(0f, 270f), false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(decimal(score, 0), fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("analysis-score"))
                    Text("/ 100", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Score heurístico", fontWeight = FontWeight.SemiBold)
                Text("${decimal(snapshot.number("coverage"), 0)}% de cobertura", style = MaterialTheme.typography.bodySmall)
                Text("Modelo Investment Radar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Avaliação quantitativa própria; não representa consenso de analistas.", style = MaterialTheme.typography.labelSmall)
    }
    FinanceCard(title = "Componentes do score") {
        linkedMapOf("quality" to "Qualidade", "growth" to "Crescimento", "valuation" to "Valuation", "momentum" to "Momentum", "risk" to "Risco").forEach { (key, label) ->
            val value = snapshot.optJSONObject("breakdown")?.number(key)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(decimal(value, 0), fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { ((value ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = accent, trackColor = grid)
        }
    }
}

@Composable internal fun ValuationDashboard(snapshot: JSONObject) {
    FinanceCard(title = "Intervalo de valor justo") {
        MetricTiles(listOf("fairLow" to "Limite inferior", "fairHigh" to "Limite superior", "buyBelow" to "Cenário de compra abaixo de", "expensiveAbove" to "Cenário caro acima de").map { (key, label) ->
            VisualMetric(key, label, money(snapshot.number(key), snapshot.text("currency")), "Modelo Investment Radar",
                "Cenário de múltiplos de resultados calculado pelo núcleo C++. Não é um preço-alvo de analistas.")
        })
        Text("Cenários do modelo · não são preços-alvo de analistas", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun AnalystDashboard(snapshot: JSONObject) {
    val data = snapshot.optJSONObject("analysts") ?: return
    val labels = listOf("strongBuy" to "Compra forte", "buy" to "Compra", "hold" to "Manter", "sell" to "Venda", "strongSell" to "Venda forte")
    val colors = listOf(FinanceGreen, Color(0xFF65A66E), Color(0xFFD29A26), Color(0xFFDD8055), FinanceRed)
    val maximum = labels.mapNotNull { data.number(it.first) }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    FinanceCard(title = "Opinião dos analistas", modifier = Modifier.testTag("analyst-consensus")) {
        val counts = labels.map { data.number(it.first) }
        if (counts.all { it != null }) {
            Text("${decimal(counts.filterNotNull().sum(), 0)} recomendações", style = MaterialTheme.typography.titleMedium)
        }
        if (counts.all { it == null }) {
            Text("Distribuição de recomendações indisponível nesta fonte.", style = MaterialTheme.typography.bodySmall)
        } else labels.forEachIndexed { i, (key, label) ->
            val n = data.number(key)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label, Modifier.width(106.dp), style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { ((n ?: 0.0) / maximum).toFloat() }, Modifier.weight(1f).height(8.dp), color = colors[i])
                Text(decimal(n, 0), Modifier.width(26.dp), fontWeight = FontWeight.Bold)
            }
        }
        if (data.number("target") != null) {
            HorizontalDivider()
            MetricRow("Preço-alvo de consenso", money(data.number("target"), data.text("currency")))
            val current = snapshot.number("price")
            val target = data.number("target")!!
            if (current != null && current > 0 && target > 0 && data.text("currency").isNotBlank() && data.text("currency") == snapshot.text("currency")) {
                val ink = MaterialTheme.colorScheme.primary
                val track = MaterialTheme.colorScheme.outlineVariant
                Canvas(Modifier.fillMaxWidth().height(50.dp).semantics { contentDescription = "Preço atual ${money(current, data.text("currency"))}, alvo ${money(target, data.text("currency"))}" }) {
                    val left = 12.dp.toPx(); val right = size.width - left
                    drawLine(track, Offset(left, size.height / 2), Offset(right, size.height / 2), 3.dp.toPx(), StrokeCap.Round)
                    val span = maxOf(current, target) * 1.15
                    drawCircle(ink, 6.dp.toPx(), Offset(left + (current / span).toFloat() * (right - left), size.height / 2))
                    drawCircle(ink, 8.dp.toPx(), Offset(left + (target / span).toFloat() * (right - left), size.height / 2), style = Stroke(2.dp.toPx()))
                }
                Text("● Atual ${money(current, data.text("currency"))}    ○ Alvo ${money(target, data.text("currency"))}", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (data.number("targetLow") != null || data.number("targetHigh") != null || data.number("targetMedian") != null) {
            MetricRow("Alvo mínimo", money(data.number("targetLow"), data.text("currency")))
            MetricRow("Alvo mediano", money(data.number("targetMedian"), data.text("currency")))
            MetricRow("Alvo máximo", money(data.number("targetHigh"), data.text("currency")))
        }
        if (data.number("target") == null) {
            Text("Preço-alvo não disponibilizado pelo provider para este ativo ou plano.", style = MaterialTheme.typography.bodySmall)
        }
        Text(data.text("source").ifBlank { "Fonte não indicada" }, style = MaterialTheme.typography.labelSmall)
        if (counts.any { it != null }) Text("Recomendações · ${data.text("asOf").ifBlank { "Período não indicado" }}", style = MaterialTheme.typography.labelSmall)
        if (data.number("target") != null) Text("Preço-alvo · ${data.text("targetAsOf").ifBlank { "Período não indicado" }}", style = MaterialTheme.typography.labelSmall)
    }
}

internal fun compactAmount(value: Double?): String {
    if (value == null) return "—"
    val unit = if (abs(value) >= 1e9) 1e9 to " mil M" else if (abs(value) >= 1e6) 1e6 to " M" else if (abs(value) >= 1e3) 1e3 to " mil" else 1.0 to ""
    return decimal(value / unit.first, if (unit.first == 1.0) 2 else 1) + unit.second
}

@Composable internal fun FinancialHistoryDashboard(snapshot: JSONObject) {
    val data = snapshot.optJSONArray("financialHistory")?.objects().orEmpty()
    if (data.isEmpty()) return
    var frequency by rememberSaveable(snapshot.text("symbol")) { mutableStateOf(if (data.any { it.text("frequency") == "annual" }) "annual" else "quarterly") }
    val periods = data.filter { it.text("frequency") == frequency }
    // A currency change forms another series; do not compare amounts across units.
    val units = periods.map { it.text("currency") }.distinct()
    var unit by rememberSaveable(snapshot.text("symbol"), frequency) { mutableStateOf(units.firstOrNull().orEmpty()) }
    val rows = periods.filter { it.text("currency") == unit }.sortedBy { it.text("period") }.takeLast(5)
    FinanceCard(title = "Receita vs. lucro", modifier = Modifier.testTag("financial-history")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("annual" to "Anual", "quarterly" to "Trimestral").forEach { (key, label) ->
                FilterChip(frequency == key, { frequency = key }, { Text(label) }, enabled = data.any { it.text("frequency") == key }, modifier = Modifier.testTag("financial-$key"))
            }
        }
        if (units.size > 1) Row(Modifier.horizontalScroll(rememberScrollState())) {
            units.forEach { u -> FilterChip(unit == u, { unit = u }, { Text(u.ifBlank { "Moeda desconhecida" }) }) }
        }
        ComparisonChart(rows, "revenue", "netIncome", "Receita", "Lucro líquido", unit, "financial", false)
    }
}

@Composable internal fun EarningsDashboard(snapshot: JSONObject) {
    val rows = snapshot.optJSONArray("earningsHistory")?.objects().orEmpty().sortedBy { it.text("period") }.takeLast(5)
    if (rows.isEmpty()) return
    FinanceCard(title = "Lucro por ação · EPS", modifier = Modifier.testTag("earnings-history")) {
        ComparisonChart(rows, "actual", "estimated", "Realizado", "Estimado", "unidade do provider", "earnings", true)
    }
}

/** Native Canvas, shared scale (including negative values), touch/drag inspection
 * and keyboard/TalkBack-accessible period buttons. Missing values stay missing. */
@Composable private fun ComparisonChart(rows: List<JSONObject>, firstKey: String, secondKey: String, firstLabel: String, secondLabel: String, currency: String, tag: String, dots: Boolean) {
    if (rows.isEmpty()) return
    var selection by remember(rows) { mutableIntStateOf(rows.lastIndex) }
    val selected = rows[selection.coerceIn(0, rows.lastIndex)]
    val firstColor = MaterialTheme.colorScheme.primary
    val secondColor = Color(0xFFE4A33C)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = rows.flatMap { listOfNotNull(it.number(firstKey), it.number(secondKey)) }
    val minimum = (values.minOrNull() ?: 0.0).coerceAtMost(0.0)
    val maximum = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
    val spread = (maximum - minimum).takeIf { it > 0 } ?: 1.0
    val low = minimum - spread * .08; val high = maximum + spread * .12
    fun formatted(value: Double?) = if (dots) decimal(value) else compactAmount(value)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(Triple(firstLabel, firstKey, firstColor), Triple(secondLabel, secondKey, secondColor)).forEach { (label, key, color) ->
            Column(Modifier.weight(1f)) {
                Text("● $label", color = color, style = MaterialTheme.typography.labelMedium)
                Text(formatted(selected.number(key)), fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.testTag("$tag-$key-value"))
            }
        }
    }
    Text(selected.text("period"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("$tag-selected-period"))
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(56.dp).height(174.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(formatted(high), style = MaterialTheme.typography.labelSmall)
            Text(formatted((high + low) / 2), style = MaterialTheme.typography.labelSmall)
            Text(formatted(low), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.weight(1f).height(174.dp).testTag("$tag-chart")
            .semantics { contentDescription = "$firstLabel e $secondLabel, ${rows.size} períodos. Selecionado ${selected.text("period")}: ${formatted(selected.number(firstKey))} e ${formatted(selected.number(secondKey))}. $currency" }
            .pointerInput(rows) { detectTapGestures { p -> selection = (p.x / size.width * rows.size).toInt().coerceIn(0, rows.lastIndex) } }
            .pointerInput(rows) { detectDragGestures { change, _ -> change.consume(); selection = (change.position.x / size.width * rows.size).toInt().coerceIn(0, rows.lastIndex) } }) {
            fun y(value: Double) = ((1 - (value - low) / (high - low)) * size.height).toFloat()
            for (i in 0..3) { val yy = size.height * i / 3; drawLine(grid, Offset(0f, yy), Offset(size.width, yy), 1f) }
            val band = size.width / rows.size
            rows.forEachIndexed { i, row ->
                val center = (i + .5f) * band
                if (i == selection) drawRect(firstColor.copy(alpha = .05f), Offset(i * band, 0f), Size(band, size.height))
                listOf(Triple(firstKey, firstColor, -.17f), Triple(secondKey, secondColor, .17f)).forEach { (key, color, offset) ->
                    row.number(key)?.let { value ->
                        val x = center + offset * band
                        if (dots) {
                            drawCircle(color, 6.dp.toPx(), Offset(x, y(value)), style = if (key == secondKey) Stroke(2.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill)
                        } else {
                            val yy = y(value); val zero = y(0.0)
                            drawRoundRect(color, Offset(x - band * .13f, minOf(yy, zero)), Size(band * .26f, abs(yy - zero).coerceAtLeast(1f)), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                        }
                    }
                }
            }
            drawLine(grid, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1.5f)
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { i, row ->
            FilterChip(i == selection, { selection = i }, { Text(if (dots || row.text("frequency") == "quarterly") row.text("period").take(7) else row.text("period").take(4)) }, Modifier.testTag("$tag-period-$i"))
        }
    }
    Text("${selected.text("source")} · ${currency.ifBlank { "moeda não indicada" }} · toca num período para explorar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
