package com.investmentradar.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Personal research notes; never provider credentials or automated advice. */
internal data class InvestmentThesis(val symbol: String, val rationale: String = "", val risks: String = "", val horizon: String = "", val review: String = "") {
    fun json() = JSONObject().put("symbol", symbol).put("rationale", rationale).put("risks", risks).put("horizon", horizon).put("review", review)
}
internal class ResearchWorkspaceStore(context: Context) {
    private val prefs = context.getSharedPreferences("research_workspace_v1", 0)
    fun selection(): List<String> = runCatching { JSONArray(prefs.getString("selection", "[]")).strings().filter(::validSymbol).distinct().take(3) }.getOrDefault(emptyList())
    fun saveSelection(values: List<String>) { check(prefs.edit().putString("selection", JSONArray(values).toString()).commit()) }
    fun theses(): Map<String, InvestmentThesis> = runCatching {
        JSONArray(prefs.getString("theses", "[]")).objects().associate { row ->
            val symbol = row.getString("symbol")
            symbol to InvestmentThesis(symbol, row.text("rationale"), row.text("risks"), row.text("horizon"), row.text("review"))
        }
    }.getOrDefault(emptyMap())
    fun save(thesis: InvestmentThesis): String? {
        if (!validSymbol(thesis.symbol)) return "Escolhe um ativo válido."
        if (listOf(thesis.rationale, thesis.risks).any { it.length > 4000 } || thesis.horizon.length > 120) return "Reduz o tamanho das notas."
        if (thesis.review.isNotBlank() && runCatching { LocalDate.parse(thesis.review) }.isFailure) return "Usa uma data válida: AAAA-MM-DD."
        val values = theses() + (thesis.symbol to thesis)
        return if (prefs.edit().putString("theses", JSONArray(values.values.map { it.json() }).toString()).commit()) null else "Não foi possível guardar a tese."
    }
}
internal data class ResearchRoute(val title: String, val query: String, val type: String, val explanation: String)
internal val researchRoutes = listOf(
    ResearchRoute("Todos", "", "Todos", "Explora o catálogo por nome, ticker ou ISIN. A presença no catálogo não é uma recomendação."),
    ResearchRoute("Mundial", "world", "ETF", "Fundos com «world» no nome. Confirma o índice: MSCI World e All-World não cobrem os mesmos mercados."),
    ResearchRoute("Europa", "europe", "ETF", "Fundos com «Europe» no nome. Compara países, índice e concentração; não implica menor risco."),
    ResearchRoute("Emergentes", "emerging", "ETF", "Fundos com «emerging» no nome. Confirma países, concentração e riscos cambiais."),
    ResearchRoute("Obrigações", "bond", "ETF", "Fundos com «bond» no nome. Confirma duração, qualidade de crédito e cobertura cambial; podem perder valor."),
    ResearchRoute("Empresas", "", "Ação", "Pesquisa empresas e compara os dados recebidos. Crescimento e preço atrativo precisam de métricas verificadas.")
)
