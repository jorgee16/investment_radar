package com.investmentradar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable internal fun AssetKnowledgeTeaser(asset: DirectoryAsset, open: () -> Unit) {
    FinanceCard(title = "Conhece este investimento") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssetIdentityIcon(asset)
            Column(Modifier.weight(1f)) {
                Text(asset.name, style = MaterialTheme.typography.titleMedium)
                Text(listOf(asset.type, asset.sector, asset.country).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(open, Modifier.testTag("asset-open-profile")) { Text(if (asset.type == "ETF") "Objetivo, composição e ficha →" else "Atividade, contexto e ficha →") }
    }
}

/** Educational context, never a company forecast or an analyst recommendation. */
internal fun sectorContext(sector: String): Pair<String, String> = when (sector) {
    "Indústria" -> "Encomendas e capacidade" to "Acompanha carteira de encomendas, entregas, utilização das fábricas e custos dos fornecedores. Encomendas não equivalem a receita já realizada."
    "Tecnologia" -> "Crescimento e investimento" to "Compara procura, receitas recorrentes, margens e investimento em investigação. Crescimento rápido pode já estar refletido numa avaliação elevada."
    "Finanças" -> "Capital e qualidade dos ativos" to "Acompanha rentabilidade, capital regulatório e incumprimentos. Taxas de juro e risco de crédito podem alterar os resultados."
    "Energia" -> "Preços e fluxo de caixa" to "Observa preços da energia, custos de produção, investimento e dívida. As margens podem variar bastante ao longo do ciclo."
    "Saúde" -> "Produtos e investigação" to "Acompanha vendas por produto, patentes e progresso regulatório. Um ensaio clínico ou uma decisão regulatória pode alterar as perspetivas."
    "Consumo essencial" -> "Volumes e poder de preço" to "Distingue crescimento de preços de crescimento em volume. Acompanha custos, margens e capacidade de manter a procura."
    "Consumo discricionário" -> "Procura e ciclo económico" to "Acompanha vendas, inventários e margens. Rendimento das famílias, crédito e confiança influenciam a procura."
    "Comunicações" -> "Clientes e monetização" to "Acompanha crescimento de clientes, receita por utilizador, custos de conteúdo e investimento em infraestrutura."
    "Serviços públicos" -> "Regulação e financiamento" to "Observa tarifas, investimento, dívida e custo de financiamento. A regulação pode limitar ou estabilizar a rentabilidade."
    "Materiais" -> "Ciclo e custos" to "Compara preços de venda, volumes, custos de energia e investimento. Os resultados dependem da procura e dos preços das matérias-primas."
    "Imobiliário" -> "Ocupação e dívida" to "Acompanha ocupação, rendas, vencimentos da dívida e custo de financiamento. Taxas de juro afetam rendimentos e avaliações."
    else -> "Antes de investir" to "Confirma o modelo de negócio, a origem das receitas, os riscos, a dívida e a evolução dos resultados. Uma cotação isolada não permite avaliar o investimento."
}

@Composable internal fun AssetKnowledgeScreen(asset: DirectoryAsset, snapshot: JSONObject?) {
    val context = LocalContext.current
    val profile = snapshot?.optJSONObject("profile")
    val fund = snapshot?.optJSONObject("fund")
    val providerDescription = profile?.text("description").orEmpty()
    val description = providerDescription.ifBlank { asset.summary }
    val providerOrigin = profile?.text("source").orEmpty()
    val sector = snapshot?.text("sector")?.takeIf(String::isNotBlank) ?: asset.sector
    val isFund = asset.type == "ETF" || snapshot?.text("instrumentType") in listOf("etf", "fund")
    var expanded by rememberSaveable(asset.symbol) { mutableStateOf(false) }
    FinanceCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssetIdentityIcon(asset)
            Column(Modifier.weight(1f)) {
                Text(assetDisplayName(asset.symbol, snapshot?.text("name").orEmpty(), asset), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("asset-profile-name"))
                Text(listOf(asset.symbol, asset.exchange).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PlanPill(asset.type); if (sector != "Sem setor") PlanPill(sector)
            if (asset.country.isNotBlank()) PlanPill(asset.country)
        }
        Text(if (isFund) "O que acompanha este fundo" else "O que faz esta empresa", style = MaterialTheme.typography.titleMedium)
        if (description.isNotBlank()) {
            Text(if (expanded) description else description.take(420) + if (description.length > 420) "…" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("asset-profile-description"))
            if (description.length > 420) TextButton({ expanded = !expanded }, Modifier.testTag("asset-profile-expand")) { Text(if (expanded) "Ler menos −" else "Ler descrição completa +") }
            Text(if (providerDescription.isNotBlank()) "Descrição do provider · $providerOrigin · data de revisão não fornecida"
                else "Descrição original em inglês · FinanceDatabase · versão do catálogo: 20/10/2026. A fonte não indica a data de revisão desta descrição.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Descrição ainda não disponível nesta fonte. A ficha mantém os identificadores conhecidos; os dados em falta não são preenchidos por suposição.", modifier = Modifier.testTag("asset-profile-description-missing"))
        }
    }
    FinanceCard(title = "Ficha do investimento") {
        val rows = linkedMapOf(
            "Tipo" to asset.type, "ISIN" to (fund?.text("isin")?.takeIf(String::isNotBlank) ?: asset.isin),
            "Bolsa / mercado" to asset.exchange, "Moeda da listagem" to asset.currency,
            (if (isFund) "País da fonte" else "País da empresa") to (profile?.text("country")?.takeIf(String::isNotBlank) ?: asset.country),
            "Indústria" to (profile?.text("industry")?.takeIf(String::isNotBlank) ?: asset.industry),
            "Dimensão no catálogo" to asset.marketCap)
        rows.filterValues(String::isNotBlank).forEach { (key, value) -> MetricRow(key, value) }
        if (isFund) {
            MetricRow("Rendimentos", when (asset.accumulating) { true -> "Acumulação"; false -> "Distribuição"; null -> "Por confirmar" })
            val issuer = fund?.text("issuer")?.takeIf(String::isNotBlank) ?: asset.issuer
            if (issuer.isNotBlank()) MetricRow("Gestora", issuer)
            fund?.text("index")?.takeIf(String::isNotBlank)?.let { MetricRow("Índice de referência", it) }
            fund?.text("domicile")?.takeIf(String::isNotBlank)?.let { MetricRow("Domicílio do fundo", it) }
            if (asset.region !in listOf("Por confirmar", "Desconhecida")) MetricRow("Exposição conhecida", asset.region)
            Text("A bolsa e a moeda da listagem não indicam os países, setores ou moedas dos investimentos do fundo.", style = MaterialTheme.typography.bodySmall)
        }
        Text("Fonte dos identificadores: ${asset.source}. O catálogo não confirma negociação na XTB, liquidez ou cobertura dos providers.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val website = profile?.text("website")?.takeIf(String::isNotBlank) ?: asset.website
        val original = android.net.Uri.parse(website)
        val uri = if (original.scheme.equals("http", true)) original.buildUpon().scheme("https").build() else original
        if (website.isNotBlank() && ProviderPolicy.mayOpenExternal(uri.scheme, uri.host, true, true)) TextButton({ openArticle(context, uri.toString()) }, Modifier.testTag("asset-official-site")) { Text("Site da empresa / gestora ↗") }
    }
    if (isFund) {
        FundComposition(fund)
        FinanceCard(title = "Antes de comparar ETFs") {
            Text("Compara índice, custos correntes, método de réplica, dimensão, tracking difference e política de rendimentos no documento do emitente. ETFs temáticos ou alavancados podem ter riscos diferentes de um fundo mundial diversificado.", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        val (title, text) = sectorContext(asset.sector)
        FinanceCard(title = title) {
            Text(text)
            Text("Contexto educativo do setor · não representa o plano publicado pela empresa.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FinanceCard(title = "Planos e perspetivas") {
            Text("A orientação da empresa e as estimativas de analistas são fontes diferentes. O catálogo de referência não contém guidance anual verificado.")
            if (snapshot?.optJSONObject("analysts") != null) {
                Text("O consenso recebido está disponível abaixo; é uma estimativa dos analistas, não uma promessa da empresa.", style = MaterialTheme.typography.bodySmall)
            } else Text("Sem previsões verificadas recebidas para este ativo. Consulta os resultados e apresentações a investidores no site da empresa.", style = MaterialTheme.typography.bodySmall)
        }
        snapshot?.takeIf { it.optJSONObject("analysts") != null }?.let { AnalystDashboard(it) }
    }
}

@Composable internal fun FundComposition(fund: JSONObject?) {
    val holdings = fund?.optJSONArray("holdings")?.objects().orEmpty()
    val sectors = fund?.optJSONArray("sectors")?.objects().orEmpty()
    FinanceCard(title = "Composição do fundo") {
        if (holdings.isEmpty() && sectors.isEmpty()) {
            Text("Sem composição recebida", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("fund-composition-unavailable"))
            Text("Os pesos das empresas e setores precisam de dados da carteira do ETF. Não são deduzidos do nome, do índice ou da moeda.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("${fund?.text("compositionSource").orEmpty()} · ${fund?.text("compositionDate")?.takeIf(String::isNotBlank) ?: "Data da composição não fornecida"}", style = MaterialTheme.typography.labelSmall)
            if (holdings.isNotEmpty()) {
                Text("Principais posições", style = MaterialTheme.typography.titleMedium)
                holdings.forEach { CompositionBar(it.text("name").ifBlank { it.text("symbol") }, it.number("weight")) }
                fund?.number("otherWeight")?.let { CompositionBar("Outras posições e caixa", it) }
            }
            if (sectors.isNotEmpty()) {
                Text("Setores da componente acionista", style = MaterialTheme.typography.titleMedium)
                sectors.forEach { CompositionBar(it.text("name"), it.number("weight")) }
            }
        }
    }
}
@Composable private fun CompositionBar(label: String, weight: Double?) {
    if (weight == null || weight !in 0.0..100.0) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        MetricRow(label, "${decimal(weight, 2)}%")
        LinearProgressIndicator(progress = { (weight / 100).toFloat() }, modifier = Modifier.fillMaxWidth().height(7.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}
