package com.investmentradar.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter
import com.investmentradar.desktop.model.*

@Composable
fun AssetWorkspace(state:RadarUiState,onIntent:(RadarIntent)->Unit,modifier:Modifier=Modifier){
    val asset=state.selected
    Column(modifier.background(RadarColors.Background).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        if(asset==null){
            PanelSurface(Modifier.fillMaxSize()){
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                    Text(if(state.loading)"Loading market data…" else "Select an asset",color=RadarColors.TextMuted)
                }
            }
            return
        }
        AssetHeader(state)
        AssetTabs(state.selectedTab){onIntent(RadarIntent.SelectAssetTab(it))}
        when(state.selectedTab){
            AssetTab.OVERVIEW -> OverviewTab(state,onIntent,Modifier.weight(1f))
            AssetTab.FINANCIALS -> PanelSurface(Modifier.weight(1f)){ FinancialsTab(asset) }
            AssetTab.VALUATION -> PanelSurface(Modifier.weight(1f)){ ValuationTab(asset) }
            AssetTab.EARNINGS -> PanelSurface(Modifier.weight(1f)){ EarningsTab(state.assetEarnings) }
            AssetTab.NEWS -> PanelSurface(Modifier.weight(1f)){ NewsTab(state.news) }
        }
    }
}

@Composable
private fun AssetHeader(state:RadarUiState){
    val a=state.selected?:return
    val changeColor=when{
        a.changePct==null->RadarColors.TextMuted
        a.changePct>=0->RadarColors.Positive
        else->RadarColors.Negative
    }
    PanelSurface{
        Row(Modifier.fillMaxWidth().height(84.dp).padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically){
            Column{
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(a.name.ifBlank{a.symbol},color=RadarColors.Text,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    Surface(color=RadarColors.PanelRaised,shape=MaterialTheme.shapes.small){
                        Text(a.symbol,color=RadarColors.Accent,fontFamily=FontFamily.Monospace,fontSize=9.sp,modifier=Modifier.padding(horizontal=7.dp,vertical=4.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(listOfNotNull(a.sector?.takeIf{it.isNotBlank()},a.sources.quote.takeIf{it!="none"}).joinToString(" · ").ifBlank{"Market data"},color=RadarColors.TextMuted,fontSize=9.sp)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment=Alignment.End){
                Text(FinancialFormatter.price(a.price,a.currency),color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text(FinancialFormatter.percent(a.changePct),color=changeColor,fontFamily=FontFamily.Monospace,fontSize=10.sp)
            }
            Spacer(Modifier.width(28.dp))
            Column(horizontalAlignment=Alignment.End){
                Text("RADAR SCORE",color=RadarColors.TextMuted,fontSize=7.sp)
                Text(a.score?.let{"${"%.0f".format(it)}/100"}?:"—",color=RadarColors.Accent,fontFamily=FontFamily.Monospace,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text(a.zone,color=RadarColors.TextMuted,fontSize=8.sp)
            }
        }
    }
}

@Composable
private fun AssetTabs(selected:AssetTab,onSelect:(AssetTab)->Unit){
    PanelSurface{
        Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
            AssetTab.entries.forEach{tab->
                val active=tab==selected
                TextButton(onClick={onSelect(tab)}){
                    Text(tab.name.lowercase().replaceFirstChar{it.uppercase()},color=if(active)RadarColors.Text else RadarColors.TextMuted,fontSize=10.sp,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(state:RadarUiState,onIntent:(RadarIntent)->Unit,modifier:Modifier=Modifier){
    val a=state.selected?:return
    Row(modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(10.dp)){
            ChartPanel(state,onIntent,Modifier.weight(1f).fillMaxWidth())
            if(a.warnings.isNotEmpty()){
                PanelSurface{
                    Text(a.warnings.joinToString(" · "),color=RadarColors.TextMuted,fontSize=9.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=9.dp))
                }
            }
        }
        Column(Modifier.width(286.dp).fillMaxHeight().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
            SignalPanel(a)
            ValuationMiniPanel(a)
            PositionPanel(state,a)
        }
    }
}

@Composable
private fun ChartPanel(state:RadarUiState,onIntent:(RadarIntent)->Unit,modifier:Modifier=Modifier){
    val a=state.selected?:return
    val history=a.history
    val candles=history.filter{it.open!=null&&it.high!=null&&it.low!=null&&it.close!=null}
    val last=candles.lastOrNull()
    val periodHigh=history.mapNotNull{it.high?:it.price}.maxOrNull()
    val periodLow=history.mapNotNull{it.low?:it.price}.minOrNull()

    PanelSurface(modifier){
        Column(Modifier.fillMaxSize()){
            Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
                Text("PRICE",color=RadarColors.TextMuted,fontSize=8.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.width(14.dp))
                ChartRange.entries.forEach{range->
                    ChartControl(range.label,state.chartRange==range){onIntent(RadarIntent.SelectChartRange(range))}
                }
                Spacer(Modifier.weight(1f))
                ChartControl("Candles",state.chartStyle==ChartStyle.CANDLES){onIntent(RadarIntent.SelectChartStyle(ChartStyle.CANDLES))}
                ChartControl("Line",state.chartStyle==ChartStyle.LINE){onIntent(RadarIntent.SelectChartStyle(ChartStyle.LINE))}
            }
            HorizontalDivider(color=RadarColors.Border)
            Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)){
                Text(a.symbol,color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                last?.let{
                    MiniQuote("O",FinancialFormatter.price(it.open,a.currency))
                    MiniQuote("H",FinancialFormatter.price(it.high,a.currency))
                    MiniQuote("L",FinancialFormatter.price(it.low,a.currency))
                    MiniQuote("C",FinancialFormatter.price(it.close,a.currency))
                }
                if(last==null){
                    MiniQuote("High",FinancialFormatter.price(periodHigh,a.currency))
                    MiniQuote("Low",FinancialFormatter.price(periodLow,a.currency))
                }
                Spacer(Modifier.weight(1f))
                Text(if(candles.size>=2)"OHLC · ${history.size} bars" else "${history.size} points",color=RadarColors.TextMuted,fontSize=8.sp)
                if(state.chartLoading){
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(13.dp),strokeWidth=1.5.dp,color=RadarColors.Accent)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)){
                if(history.isEmpty()){
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                        Text("No historical data for this period",color=RadarColors.TextMuted,fontSize=10.sp)
                    }
                }else{
                    MarketChart(history,a.currency,state.chartStyle,Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ChartControl(label:String,selected:Boolean,onClick:()->Unit){
    TextButton(onClick=onClick,contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp),colors=ButtonDefaults.textButtonColors(containerColor=if(selected)RadarColors.Selected else Color.Transparent)){
        Text(label,color=if(selected)RadarColors.Accent else RadarColors.TextMuted,fontSize=8.sp,fontFamily=FontFamily.Monospace)
    }
}

@Composable
private fun MiniQuote(label:String,value:String){
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
        Text(label,color=RadarColors.TextMuted,fontSize=8.sp,fontFamily=FontFamily.Monospace)
        Text(value,color=RadarColors.Text,fontSize=8.sp,fontFamily=FontFamily.Monospace)
    }
}

@Composable
private fun SignalPanel(a:SnapshotDto){
    PanelSurface{
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("INVESTMENT RADAR",color=RadarColors.TextMuted,fontSize=8.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom){
                Column{
                    Text(a.score?.let{"${"%.0f".format(it)}"}?:"—",color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=30.sp,fontWeight=FontWeight.Bold)
                    Text("score / 100",color=RadarColors.TextMuted,fontSize=8.sp)
                }
                Surface(color=zoneColor(a.zone).copy(alpha=.14f),shape=MaterialTheme.shapes.small){
                    Text(a.zone,color=zoneColor(a.zone),fontSize=9.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=8.dp,vertical=5.dp))
                }
            }
            HorizontalDivider(color=RadarColors.Border)
            MetricRow("Coverage","${"%.0f".format(a.coverage)}%")
            a.breakdown.forEach{(k,v)->MetricRow(k.replaceFirstChar{it.uppercase()},v?.let{"%.0f".format(it)}?:"—")}
            if(a.reasons.isNotEmpty()){
                HorizontalDivider(color=RadarColors.Border)
                a.reasons.take(3).forEach{reason->
                    Column{
                        Text(reason.label,color=RadarColors.Text,fontSize=9.sp,fontWeight=FontWeight.SemiBold)
                        Text(reason.detail,color=RadarColors.TextMuted,fontSize=8.sp,maxLines=2)
                    }
                }
            }
        }
    }
}

@Composable
private fun ValuationMiniPanel(a:SnapshotDto){
    PanelSurface{
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            Text("VALUATION",color=RadarColors.TextMuted,fontSize=8.sp)
            MetricRow("Buy below",FinancialFormatter.price(a.buyBelow,a.currency))
            MetricRow("Fair low",FinancialFormatter.price(a.fairLow,a.currency))
            MetricRow("Fair high",FinancialFormatter.price(a.fairHigh,a.currency))
            MetricRow("Expensive",FinancialFormatter.price(a.expensiveAbove,a.currency))
        }
    }
}

@Composable
private fun PositionPanel(state:RadarUiState,a:SnapshotDto){
    val p=state.portfolio.find{it.symbol==a.symbol}
    PanelSurface{
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            Text("YOUR POSITION",color=RadarColors.TextMuted,fontSize=8.sp)
            if(p==null) Text("No position in this asset",color=RadarColors.TextMuted,fontSize=9.sp)
            else{
                MetricRow("Quantity","%.4f".format(p.qty))
                MetricRow("Average",FinancialFormatter.price(p.avg,p.currency))
                val current=if(a.price!=null&&a.currency==p.currency)p.qty*a.price else null
                MetricRow("Value",FinancialFormatter.price(current,p.currency))
                MetricRow("P/L",FinancialFormatter.price(current?.minus(p.qty*p.avg),p.currency))
            }
        }
    }
}

@Composable
private fun FinancialsTab(a:SnapshotDto){
    val rows=listOf(
        "P/E TTM" to a.pe?.let{"%.1f".format(it)},
        "Forward P/E" to a.forwardPE?.let{"%.1f".format(it)},
        "Revenue growth" to FinancialFormatter.percent(a.revenueGrowth),
        "EPS growth" to FinancialFormatter.percent(a.epsGrowth),
        "FCF margin" to FinancialFormatter.percent(a.fcfMargin),
        "ROE" to FinancialFormatter.percent(a.roe),
        "Debt / equity" to a.debtToEquity?.let{"%.2f".format(it)},
        "Earnings surprise" to FinancialFormatter.percent(a.earningsSurprise),
        "Estimate revision" to FinancialFormatter.percent(a.estimateRevision)
    )
    DataList("FINANCIALS","Core metrics",rows.map{it.first to (it.second?:"—")})
}

@Composable
private fun ValuationTab(a:SnapshotDto){
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("VALUATION",color=RadarColors.TextMuted,fontSize=8.sp)
        Text("Price zones",color=RadarColors.Text,fontWeight=FontWeight.Bold,fontSize=16.sp)
        MetricRow("Buy below",FinancialFormatter.price(a.buyBelow,a.currency))
        MetricRow("Fair range",if(a.fairLow!=null&&a.fairHigh!=null)"${FinancialFormatter.price(a.fairLow,a.currency)} – ${FinancialFormatter.price(a.fairHigh,a.currency)}" else "—")
        MetricRow("Expensive above",FinancialFormatter.price(a.expensiveAbove,a.currency))
        MetricRow("Current zone",a.zone)
        HorizontalDivider(color=RadarColors.Border)
        MetricRow("P/E TTM",a.pe?.let{"%.1f".format(it)}?:"—")
        MetricRow("Forward P/E",a.forwardPE?.let{"%.1f".format(it)}?:"—")
        if(a.thesis.isNotBlank()) Text(a.thesis,color=RadarColors.TextMuted,fontSize=10.sp)
    }
}

@Composable
private fun EarningsTab(rows:List<EarningsDto>){
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("EARNINGS",color=RadarColors.TextMuted,fontSize=8.sp)
        if(rows.isEmpty()) Text("No earnings events available",color=RadarColors.TextMuted)
        rows.forEach{e->
            Row(Modifier.fillMaxWidth().background(RadarColors.PanelRaised).padding(12.dp)){
                Text(e.date,color=RadarColors.TextMuted,fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.width(100.dp))
                Column{
                    Text("${e.symbol} · ${e.kind}",color=RadarColors.Text,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                    Text("${e.note} · ${e.source}",color=RadarColors.TextMuted,fontSize=9.sp)
                }
            }
        }
    }
}

@Composable
private fun NewsTab(rows:List<NewsDto>){
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("NEWS",color=RadarColors.TextMuted,fontSize=8.sp)
        if(rows.isEmpty()) Text("No news available",color=RadarColors.TextMuted)
        rows.forEach{n->
            Column(Modifier.fillMaxWidth().background(RadarColors.PanelRaised).padding(12.dp)){
                Text(n.title,color=RadarColors.Text,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                Text("${n.date} · ${n.source}",color=RadarColors.TextMuted,fontSize=9.sp)
            }
        }
    }
}

@Composable
private fun DataList(kicker:String,title:String,rows:List<Pair<String,String>>){
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(kicker,color=RadarColors.TextMuted,fontSize=8.sp)
        Text(title,color=RadarColors.Text,fontWeight=FontWeight.Bold,fontSize=16.sp)
        rows.forEach{MetricRow(it.first,it.second)}
    }
}

@Composable
fun MetricRow(label:String,value:String){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        Text(label,color=RadarColors.TextMuted,fontSize=9.sp)
        Text(value,color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=9.sp)
    }
}

@Composable
fun PanelSurface(modifier:Modifier=Modifier,content:@Composable ()->Unit){
    Surface(modifier=modifier,color=RadarColors.Panel,shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,RadarColors.Border),content=content)
}

private fun zoneColor(zone:String)=when(zone.uppercase()){
    "BUY","CHEAP"->RadarColors.Positive
    "EXPENSIVE","SELL"->RadarColors.Negative
    else->RadarColors.Accent
}
