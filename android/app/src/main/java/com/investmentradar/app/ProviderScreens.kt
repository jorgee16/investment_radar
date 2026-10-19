package com.investmentradar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable internal fun ProvidersScreen(state: RadarState, model: RadarViewModel) {
    val context = LocalContext.current
    var testSymbol by rememberSaveable { mutableStateOf("AAPL") }
    var testError by remember { mutableStateOf<String?>(null) }
    val drafts = remember { mutableStateMapOf<String, String>() } // Deliberately absent from saved-instance state.
    val routing = remember(state.routing) { mutableStateMapOf<String, String>().apply { putAll(state.routing) } }
    ModeBanner(state, model)
    FinanceCard(title = "Modo de dados") {
        Text("Demo apresenta dados sintéticos. Live usa apenas dados reais, sem substituir valores ausentes por dados Demo.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.demo, { model.saveSettings(JSONObject().put("demo", true)) }, { Text("Demo") }, enabled = !state.saving, modifier = Modifier.testTag("mode-demo"))
            FilterChip(!state.demo, { model.saveSettings(JSONObject().put("demo", false)) }, { Text("Live") }, enabled = !state.saving, modifier = Modifier.testTag("mode-live"))
        }
        if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    FinanceCard(title = "Chaves de API") {
        Text("Cria uma conta no provider e copia a chave do dashboard. Basta configurar os providers de que precisas. As chaves guardadas nunca são devolvidas à interface.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        providerKeys.forEach { (id, _) ->
            Text(providerNames.getValue(id), fontWeight = FontWeight.Bold)
            Text(if (id in state.configured) "Chave guardada · Android Keystore" else "Sem chave configurada", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("key-status-$id"))
            OutlinedTextField(drafts[id].orEmpty(), { drafts[id] = it }, label = { Text(if (id in state.configured) "Substituir chave" else "Introduzir chave") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth().testTag("key-$id"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ openArticle(context, providerLinks.getValue(id)) }) { Text("Obter chave ↗") }
                if (id in state.configured) TextButton({ drafts.remove(id); model.saveSettings(JSONObject().put(providerKeys.getValue(id), "")) }, enabled = !state.saving, modifier = Modifier.testTag("clear-key-$id")) { Text("Remover chave") }
            }
            HorizontalDivider()
        }
        Button({
            val update = JSONObject()
            drafts.forEach { (id, value) -> if (value.isNotBlank()) update.put(providerKeys.getValue(id), value.trim()) }
            drafts.clear(); model.saveSettings(update)
        }, enabled = !state.saving && drafts.values.any(String::isNotBlank), modifier = Modifier.testTag("save-keys")) { Text("Guardar chaves") }
        Text("EODHD gratuito: uma cotação bem-sucedida não confirma acesso a fundamentais. Histórico, bolsas europeias e estimativas dependem do plano. Quotas gratuitas dos restantes providers também são limitadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    FinanceCard(title = "Testar ligação") {
        Text("Guarda primeiro as chaves. O teste consulta cotações, fundamentais, estimativas, histórico, resultados e notícias para este símbolo, mesmo em Demo. Pode consumir vários pedidos da quota.", style = MaterialTheme.typography.bodySmall)
        Text("Fundamentais recebidos não garantem todas as métricas. Surpresas de resultados não são previsões: o P/E estimado pode continuar indisponível.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(testSymbol, { testSymbol = it.take(32); testError = null }, label = { Text("Ticker de teste") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("test-symbol"))
        Button({ testError = model.testProviders(testSymbol) }, enabled = !state.testing && !state.saving, modifier = Modifier.testTag("test-providers")) { Text(if (state.testing) "A testar…" else "Testar todos") }
        if (testError != null) Text(testError!!, color = MaterialTheme.colorScheme.error)
        state.reports.forEach { report ->
            HorizontalDivider()
            Text(report.text("name"), fontWeight = FontWeight.Bold)
            Text(report.text("message"), modifier = Modifier.testTag("report-${report.text("id")}"), color = if (report.optBoolean("connected")) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
            Text("${report.text("symbol")} · ${report.text("code")}", style = MaterialTheme.typography.labelSmall)
            if (report.number("price") != null) Text(money(report.number("price"), report.text("currency")))
            val results = report.optJSONObject("capabilities")
            capabilities.forEach { (capability, label) ->
                val code = results?.text(capability).orEmpty()
                if (code.isNotBlank()) MetricRow(label, when (code) {
                    "ok" -> "Dados recebidos"
                    "missing_key" -> "Chave em falta"
                    "subscription" -> "Sem permissão no plano"
                    "authentication" -> "Chave inválida / expirada"
                    "rate_limit" -> "Quota atingida"
                    "no_data" -> "Sem dados para este símbolo"
                    "unsupported" -> "Não suportado"
                    "network" -> "Falha de ligação"
                    else -> "Pedido sem sucesso"
                }, modifier = Modifier.testTag("capability-${report.text("id")}-$capability"))
            }
            if (results?.text("fundamentals") == "ok") {
                MetricRow("P/E devolvido", decimal(report.number("pe")), modifier = Modifier.testTag("report-pe-${report.text("id")}"))
                MetricRow("P/E estimado devolvido", decimal(report.number("forwardPE")))
                if (report.number("epsTTM") != null) Text("EPS dos últimos 12 meses recebido. O cálculo do P/E exige moeda compatível com a cotação.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    FinanceCard(title = "Fonte preferida por tipo de dados") {
        Text("Automático tenta os providers configurados com a capacidade necessária.", style = MaterialTheme.typography.bodySmall)
        capabilities.forEach { (capability, title) ->
            var expanded by remember { mutableStateOf(false) }
            val supported = state.providers.filter { it.optJSONArray("capabilities")?.strings()?.contains(capability) == true }.map { it.text("id") }
            Column {
                OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().testTag("route-$capability"), enabled = !state.saving) {
                    Text("$title: ${providerNames[routing[capability]] ?: "Automático"}")
                }
                DropdownMenu(expanded, { expanded = false }) {
                    (listOf("auto") + supported).distinct().forEach { id -> DropdownMenuItem(text = { Text(providerNames[id] ?: id) },
                        onClick = { routing[capability] = id; expanded = false }, modifier = Modifier.testTag("route-option-$capability-$id")) }
                }
            }
        }
        Button({ model.saveSettings(JSONObject().put("routing", JSONObject(routing.toMap()))) }, enabled = !state.saving, modifier = Modifier.testTag("save-routing")) { Text("Guardar fontes") }
    }
    FinanceCard(title = "Capacidades dos providers") {
        state.providers.forEach { provider ->
            Text(provider.text("name"), fontWeight = FontWeight.Bold)
            Text(provider.optJSONArray("capabilities")?.strings()?.map { capabilities[it] ?: it }?.joinToString(" · ").orEmpty(), style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
        }
    }
}
