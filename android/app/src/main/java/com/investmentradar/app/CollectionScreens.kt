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
                listOf(10,20,50,100,240,500,1000).forEach { limit -> DropdownMenuItem(text = { Text("$limit pedidos/dia") }, onClick = { budgetMenu=false; model.savePolling(dailyLimit=limit) }, modifier = Modifier.testTag("polling-budget-$limit")) }
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
