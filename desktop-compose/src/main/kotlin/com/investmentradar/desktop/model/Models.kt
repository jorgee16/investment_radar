package com.investmentradar.desktop.model

import kotlinx.serialization.Serializable

@Serializable data class QuoteDto(val symbol:String="", val name:String="", val price:Double?=null, val currency:String="", val changePct:Double?=null, val timestamp:Long?=null, val source:String?=null)
@Serializable data class HistoryPointDto(
    val label:String="",
    val price:Double?=null,
    val open:Double?=null,
    val high:Double?=null,
    val low:Double?=null,
    val close:Double?=null
)
@Serializable data class SnapshotDto(val symbol:String="", val name:String="", val sector:String?=null, val price:Double?=null, val currency:String="", val changePct:Double?=null, val score:Double?=null, val zone:String="N/A", val history:List<HistoryPointDto> = emptyList(), val coverage:Double=0.0)
@Serializable data class StatusDto(
    val mode:String="demo",
    val configured:Boolean=false,
    val live:Boolean=false,
    val routing:Map<String,String> = emptyMap()
)
@Serializable data class EarningsDto(val date:String="", val symbol:String="", val kind:String="", val note:String="", val source:String="")
@Serializable data class ProviderDto(
    val id:String="",
    val name:String="",
    val enabled:Boolean=false,
    val connected:Boolean=false,
    val status:String="",
    val capabilities:List<String> = emptyList(),
    val testCode:String?=null,
    val testedAt:Long?=null,
    val testedSymbol:String?=null
)
@Serializable data class ProviderSettingsDto(
    val eodhdConfigured:Boolean=false,
    val fmpConfigured:Boolean=false,
    val twelveConfigured:Boolean=false,
    val keySources:Map<String,String> = emptyMap(),
    val demo:Boolean=true
)
@Serializable data class ProviderTestDto(
    val id:String="",
    val name:String="",
    val symbol:String="",
    val code:String="",
    val message:String="",
    val connected:Boolean=false,
    val price:Double?=null,
    val currency:String=""
)

data class WatchItem(val symbol:String, val name:String, val price:Double?, val currency:String, val changePct:Double?, val sparkline:List<Double>)
data class AllocationSlice(val label:String, val value:Double)
enum class AppPage { RADAR, PROVIDERS }

data class RadarUiState(
    val loading:Boolean=true,
    val status:StatusDto=StatusDto(),
    val watchlist:List<WatchItem> = emptyList(),
    val selectedSymbol:String="AIR.PA",
    val selected:SnapshotDto?=null,
    val allocation:List<AllocationSlice> = emptyList(),
    val providers:List<ProviderDto> = emptyList(),
    val providerSettings:ProviderSettingsDto=ProviderSettingsDto(),
    val providerTests:Map<String,ProviderTestDto> = emptyMap(),
    val page:AppPage=AppPage.RADAR,
    val savingProviders:Boolean=false,
    val error:String?=null
)

sealed interface RadarIntent {
    data class SelectTicker(val symbol:String):RadarIntent
    data class Navigate(val page:AppPage):RadarIntent
    data class SaveProviders(
        val demo:Boolean,
        val eodhdKey:String?,
        val fmpKey:String?,
        val twelveKey:String?,
        val routing:Map<String,String>
    ):RadarIntent
    data class TestProvider(val provider:String,val symbol:String):RadarIntent
    data object Refresh:RadarIntent
}
