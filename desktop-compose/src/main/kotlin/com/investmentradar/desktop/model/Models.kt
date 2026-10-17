package com.investmentradar.desktop.model

import kotlinx.serialization.Serializable

@Serializable data class QuoteDto(
    val symbol:String="", val name:String="", val price:Double?=null,
    val currency:String="", val changePct:Double?=null, val timestamp:Long?=null,
    val source:String?=null
)

@Serializable data class HistoryPointDto(
    val label:String="", val price:Double?=null,
    val open:Double?=null, val high:Double?=null, val low:Double?=null, val close:Double?=null,
    val source:String?=null
)

@Serializable data class ScoreReasonDto(
    val label:String="", val detail:String="", val impact:Double=0.0
)

@Serializable data class SnapshotSourcesDto(
    val quote:String="none", val fundamentals:String="none",
    val estimates:String="none", val history:String="none"
)

@Serializable data class SnapshotDto(
    val symbol:String="", val name:String="", val sector:String?=null,
    val price:Double?=null, val currency:String="", val reportingCurrency:String="",
    val changePct:Double?=null, val score:Double?=null, val zone:String="N/A",
    val history:List<HistoryPointDto> = emptyList(), val coverage:Double=0.0,
    val pe:Double?=null, val forwardPE:Double?=null,
    val revenueGrowth:Double?=null, val epsGrowth:Double?=null,
    val fcfMargin:Double?=null, val roe:Double?=null, val debtToEquity:Double?=null,
    val earningsSurprise:Double?=null, val estimateRevision:Double?=null,
    val buyBelow:Double?=null, val fairLow:Double?=null, val fairHigh:Double?=null,
    val expensiveAbove:Double?=null, val thesis:String="",
    val reasons:List<ScoreReasonDto> = emptyList(),
    val warnings:List<String> = emptyList(),
    val breakdown:Map<String,Double?> = emptyMap(),
    val sources:SnapshotSourcesDto = SnapshotSourcesDto()
)

@Serializable data class StatusDto(
    val mode:String="demo", val configured:Boolean=false, val live:Boolean=false,
    val provider:String="", val engine:String="", val routing:Map<String,String> = emptyMap()
)

@Serializable data class EarningsDto(
    val date:String="", val symbol:String="", val kind:String="",
    val note:String="", val source:String="",
    val epsActual:Double?=null, val epsEstimated:Double?=null
)

@Serializable data class NewsDto(
    val date:String="", val title:String="", val url:String="", val source:String=""
)

@Serializable data class ProviderDto(
    val id:String="", val name:String="", val enabled:Boolean=false, val connected:Boolean=false,
    val status:String="", val capabilities:List<String> = emptyList(),
    val testCode:String?=null, val testedAt:Long?=null, val testedSymbol:String?=null
)

@Serializable data class ProviderSettingsDto(
    val eodhdConfigured:Boolean=false, val fmpConfigured:Boolean=false,
    val twelveConfigured:Boolean=false, val alphaConfigured:Boolean=false,
    val finnhubConfigured:Boolean=false, val keySources:Map<String,String> = emptyMap(),
    val demo:Boolean=true
)

@Serializable data class ProviderTestDto(
    val id:String="", val name:String="", val symbol:String="", val code:String="",
    val message:String="", val connected:Boolean=false,
    val price:Double?=null, val currency:String=""
)

@Serializable data class PortfolioPosition(
    val symbol:String, val qty:Double, val avg:Double, val currency:String
)

data class WatchItem(
    val symbol:String, val name:String, val price:Double?, val currency:String,
    val changePct:Double?, val sparkline:List<Double>,
    val score:Double?=null, val zone:String="N/A"
)

data class AllocationSlice(val label:String, val value:Double)

enum class AppPage { DASHBOARD, RADAR, PORTFOLIO, WATCHLIST, SCREENER, EVENTS, PROVIDERS, SETTINGS }
enum class AssetTab { OVERVIEW, FINANCIALS, VALUATION, EARNINGS, NEWS }
enum class ChartRange(val label:String) {
    ONE_MONTH("1M"), THREE_MONTHS("3M"), SIX_MONTHS("6M"), YTD("YTD"),
    ONE_YEAR("1Y"), FIVE_YEARS("5Y"), MAX("MAX")
}
enum class ChartStyle { CANDLES, LINE }

data class RadarUiState(
    val loading:Boolean=true,
    val status:StatusDto=StatusDto(),
    val watchSymbols:List<String> = listOf("AIR.PA","VUAA.DE","6AQQ.DE","JPM","GS","TSM"),
    val watchlist:List<WatchItem> = emptyList(),
    val portfolio:List<PortfolioPosition> = emptyList(),
    val portfolioQuotes:Map<String,QuoteDto> = emptyMap(),
    val selectedSymbol:String="AIR.PA",
    val selected:SnapshotDto?=null,
    val selectedTab:AssetTab=AssetTab.OVERVIEW,
    val chartRange:ChartRange=ChartRange.SIX_MONTHS,
    val chartStyle:ChartStyle=ChartStyle.CANDLES,
    val chartLoading:Boolean=false,
    val earnings:List<EarningsDto> = emptyList(),
    val assetEarnings:List<EarningsDto> = emptyList(),
    val news:List<NewsDto> = emptyList(),
    val providers:List<ProviderDto> = emptyList(),
    val providerSettings:ProviderSettingsDto=ProviderSettingsDto(),
    val providerTests:Map<String,ProviderTestDto> = emptyMap(),
    val page:AppPage=AppPage.DASHBOARD,
    val savingProviders:Boolean=false,
    val error:String?=null
) {
    val allocation:List<AllocationSlice>
        get() = portfolio.map { position ->
            val quote=portfolioQuotes[position.symbol]
            val value=if(quote?.price!=null&&quote.currency==position.currency)
                position.qty*quote.price
            else position.qty*position.avg
            AllocationSlice(position.symbol,value)
        }
}

sealed interface RadarIntent {
    data class SelectTicker(val symbol:String):RadarIntent
    data class Navigate(val page:AppPage):RadarIntent
    data class SelectAssetTab(val tab:AssetTab):RadarIntent
    data class SelectChartRange(val range:ChartRange):RadarIntent
    data class SelectChartStyle(val style:ChartStyle):RadarIntent
    data class AddWatch(val symbol:String):RadarIntent
    data class RemoveWatch(val symbol:String):RadarIntent
    data class AddPosition(val position:PortfolioPosition):RadarIntent
    data class RemovePosition(val index:Int):RadarIntent
    data class SaveProviders(
        val demo:Boolean,
        val eodhdKey:String?,
        val fmpKey:String?,
        val twelveKey:String?,
        val alphaKey:String?,
        val finnhubKey:String?,
        val routing:Map<String,String>
    ):RadarIntent
    data class TestProvider(val provider:String,val symbol:String):RadarIntent
    data object Refresh:RadarIntent
}
