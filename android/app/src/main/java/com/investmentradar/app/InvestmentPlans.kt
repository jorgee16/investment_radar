package com.investmentradar.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal fun planId(): String = UUID.randomUUID().toString()
internal data class PlanInstrument(val id: String = planId(), val symbol: String, val name: String, val currency: String, val isin: String = "", val weight: String = "100") {
    fun json() = JSONObject().put("id", id).put("symbol", symbol).put("name", name).put("currency", currency).put("isin", isin).put("weight", weight)
    companion object { fun read(j: JSONObject) = PlanInstrument(j.getString("id"), j.getString("symbol"), j.getString("name"), j.getString("currency"), j.optString("isin"), j.optString("weight")) }
}
internal data class PlanGroup(val id: String = planId(), val name: String, val kind: String = "custom", val weight: String = "0", val balance: String = "0", val items: List<PlanInstrument> = emptyList()) {
    fun json() = JSONObject().put("id", id).put("name", name).put("kind", kind).put("weight", weight).put("balance", balance).put("items", JSONArray(items.map { it.json() }))
    companion object { fun read(j: JSONObject) = PlanGroup(j.getString("id"), j.getString("name"), j.optString("kind", "custom"), j.optString("weight"), j.optString("balance", "0"), j.getJSONArray("items").objects().map(PlanInstrument::read)) }
}
internal data class InvestmentPlan(val id: String = planId(), val name: String, val monthly: String = "350", val years: String = "10", val mode: String = "fixed", val groups: List<PlanGroup> = emptyList(), val linkedPositionIds: Set<String> = emptySet(), val preferAccumulating: Boolean = true) {
    fun json() = JSONObject().put("id", id).put("name", name).put("monthly", monthly).put("years", years).put("mode", mode).put("groups", JSONArray(groups.map { it.json() })).put("linkedPositionIds", JSONArray(linkedPositionIds.toList())).put("preferAccumulating", preferAccumulating)
    companion object { fun read(j: JSONObject) = InvestmentPlan(j.getString("id"), j.getString("name"), j.optString("monthly", "350"), j.optString("years", "10"), j.optString("mode", "fixed"), j.getJSONArray("groups").objects().map(PlanGroup::read), j.optJSONArray("linkedPositionIds")?.strings()?.toSet() ?: emptySet(), j.optBoolean("preferAccumulating", true)) }
}
internal data class PlanLibrary(val plans: List<InvestmentPlan> = emptyList(), val activeId: String? = null)
internal class InvestmentPlanStore(context: Context) {
    private val prefs = context.getSharedPreferences("investment_plans_v1", 0)
    fun load(): PlanLibrary {
        val data = prefs.getString("library", null) ?: return PlanLibrary()
        val j = JSONObject(data)
        check(j.getInt("version") == 1) { "Formato de planos não suportado." }
        return PlanLibrary(j.getJSONArray("plans").objects().map(InvestmentPlan::read), j.optString("activeId").takeIf { it.isNotBlank() })
    }
    fun write(library: PlanLibrary) {
        val j = JSONObject().put("version", 1).put("activeId", library.activeId.orEmpty()).put("plans", JSONArray(library.plans.map { it.json() }))
        check(prefs.edit().putString("library", j.toString()).commit()) { "Não foi possível guardar os planos." }
    }
}
internal data class PlanPreset(val key: String, val name: String, val description: String)
internal val planPresets = listOf(
    PlanPreset("world", "Mundial simples", "100% ETF mundial · exposição a ações globais"),
    PlanPreset("europe", "Mundial + Europa", "80% mundial / 20% Europa · aumenta o peso europeu já presente no mundial"),
    PlanPreset("satellite", "ETF + ações", "90% mundial / 10% ações individuais · maior concentração na parcela de ações"),
    PlanPreset("reserve", "ETF + reserva", "80% mundial / 20% reserva em euros · define onde guardar a reserva"),
    PlanPreset("sectors", "Por setores", "Escolhe setores, pesos e depois os instrumentos de cada setor"),
    PlanPreset("custom", "Personalizado", "Começa vazio e cria a tua própria composição")
)
internal val investmentSectors = listOf("Tecnologia", "Saúde", "Indústria", "Finanças", "Consumo essencial", "Consumo discricionário", "Energia", "Comunicações", "Materiais", "Serviços públicos", "Imobiliário")
internal fun createInvestmentPlan(key: String): InvestmentPlan {
    val preset = planPresets.first { it.key == key }
    val groups = when (key) {
        "world" -> listOf(PlanGroup("world", "ETF mundial", "world", "100"))
        "europe" -> listOf(PlanGroup("world", "ETF mundial", "world", "80"), PlanGroup("europe", "ETF europeu", "europe", "20"))
        "satellite" -> listOf(PlanGroup("world", "ETF mundial", "world", "90"), PlanGroup("stocks", "Ações individuais", "stocks", "10"))
        "reserve" -> listOf(PlanGroup("world", "ETF mundial", "world", "80"), PlanGroup("reserve", "Reserva em euros", "reserve", "20"))
        else -> emptyList()
    }
    return InvestmentPlan(name = preset.name, groups = groups)
}
