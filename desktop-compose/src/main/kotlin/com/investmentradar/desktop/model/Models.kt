package com.investmentradar.desktop.model

import kotlinx.serialization.Serializable

@Serializable data class QuoteDto(val symbol:String="", val name:String="", val price:Double?=null, val currency:String="", val changePct:Double?=null, val timestamp:Long?=null, val source:String?=null)
@Serializable data class HistoryPointDto(val label:String="", val price:Double?=null)
@Serializable data class SnapshotDto(val symbol:String="", val name:String="", val sector:String?=null, val price:Double?=null, val currency:String="", val changePct:Double?=null, val score:Double?=null, val zone:String="N/A", val history:List<HistoryPointDto> = emptyList(), val coverage:Double=0.0)
@Serializable data class StatusDto(val mode:String="demo", val configured:Boolean=false, val live:Boolean=false)
@Serializable data class EarningsDto(val date:String="", val symbol:String="", val kind:String="", val note:String="", val source:String="")

data class WatchItem(val symbol:String, val name:String, val price:Double?, val currency:String, val changePct:Double?, val sparkline:List<Double>)
data class AllocationSlice(val label:String, val value:Double)

data class RadarUiState(
    val loading:Boolean=true,
    val status:StatusDto=StatusDto(),
    val watchlist:List<WatchItem> = emptyList(),
    val selectedSymbol:String="AIR.PA",
    val selected:SnapshotDto?=null,
    val allocation:List<AllocationSlice> = emptyList(),
    val error:String?=null
)

sealed interface RadarIntent {
    data class SelectTicker(val symbol:String):RadarIntent
    data object Refresh:RadarIntent
}
