package com.investmentradar.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter
import com.investmentradar.desktop.model.*

@Composable
fun AssetWorkspace(state:RadarUiState,onIntent:(RadarIntent)->Unit,modifier:Modifier=Modifier){
    val asset=state.selected
    Column(modifier.background(RadarColors.Background)){
        if(asset==null){
            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                Text(if(state.loading)"Loading market data…" else "Select an asset",color=RadarColors.TextMuted)
            }
            return
        }
        AssetHeader(state)
        AssetTabs(state.selectedTab){onIntent(RadarIntent.SelectAssetTab(it))}
        when(state.selectedTab){
            AssetTab.OVERVIEW -> OverviewTab(state)
            AssetTab.FINANCIALS -> FinancialsTab(asset)
            AssetTab.VALUATION -> ValuationTab(asset)
            AssetTab.EARNINGS -> EarningsTab(state.assetEarnings)
            AssetTab.NEWS -> NewsTab(state.news)
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
    Row(
        Modifier.fillMaxWidth().height(82.dp).background(RadarColors.Panel).padding(horizontal=16.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Column{
            Text(a.name.ifBlank{a.symbol},color=RadarColors.Text,fontSize=18.sp,fontWeight=FontWeight.Bold)
            Text("${a.symbol}  ·  ${a.sector?.ifBlank{"—"}?:"—"}",color=RadarColors.TextMuted,fontSize=10.sp)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment=Alignment.End){
            Text(FinancialFormatter.price(a.price,a.currency),color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text(FinancialFormatter.percent(a.changePct),color=changeColor,fontFamily=FontFamily.Monospace,fontSize=10.sp)
        }
        Spacer(Modifier.width(24.dp))
        Column(horizontalAlignment=Alignment.End){
            Text(a.score?.let{"${"%.0f".format(it)}/100"}?:"N/A",color=RadarColors.Accent,fontFamily=FontFamily.Monospace,fontSize=14.sp)
            Text(a.zone,color=RadarColors.TextMuted,fontSize=9.sp)
        }
    }
}

@Composable
private fun AssetTabs(selected:AssetTab,onSelect:(AssetTab)->Unit){
    Row(Modifier.fillMaxWidth().height(42.dp).background(RadarColors.PanelRaised)){
        AssetTab.entries.forEach{tab->
            TextButton(onClick={onSelect(tab)}){
                Text(
                    tab.name.lowercase().replaceFirstChar{it.uppercase()},
                    color=if(tab==selected)RadarColors.Accent else RadarColors.TextMuted,
                    fontSize=10.sp
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(state:RadarUiState){
    val a=state.selected?:return
    Row(Modifier.fillMaxSize()){
        Column(Modifier.weight(1f).fillMaxHeight()){
            MarketChart(a.history,a.currency,Modifier.weight(1f).fillMaxWidth().padding(12.dp))
            if(a.warnings.isNotEmpty()){
                Text(
                    a.warnings.joinToString(" · "),
                    color=RadarColors.TextMuted,
                    fontSize=9.sp,
                    modifier=Modifier.padding(horizontal=14.dp,vertical=6.dp)
                )
            }
        }
        Column(
            Modifier.width(280.dp).fillMaxHeight().background(RadarColors.Panel).padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ){
            Text("SIGNAL",color=RadarColors.TextMuted,fontSize=8.sp)
            MetricRow("Coverage","${"%.0f".format(a.coverage)}%")
            MetricRow("Score",a.score?.let{"%.0f".format(it)}?:"—")
            MetricRow("Zone",a.zone)
            a.breakdown.forEach{(k,v)->MetricRow(k.replaceFirstChar{it.uppercase()},v?.let{"%.0f".format(it)}?:"—")}
            HorizontalDivider(color=RadarColors.Border)
            Text("WHY",color=RadarColors.TextMuted,fontSize=8.sp)
            if(a.reasons.isEmpty()) Text("Not enough data",color=RadarColors.TextMuted,fontSize=10.sp)
            a.reasons.forEach{r->
                Column{
                    Text(r.label,color=RadarColors.Text,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                    Text(r.detail,color=RadarColors.TextMuted,fontSize=9.sp)
                }
            }
            state.portfolio.find{it.symbol==a.symbol}?.let{p->
                HorizontalDivider(color=RadarColors.Border)
                Text("YOUR POSITION",color=RadarColors.TextMuted,fontSize=8.sp)
                MetricRow("Quantity","%.4f".format(p.qty))
                MetricRow("Average",FinancialFormatter.price(p.avg,p.currency))
                val current=if(a.price!=null&&a.currency==p.currency)p.qty*a.price else null
                MetricRow("Value",FinancialFormatter.price(current,p.currency))
                val pl=current?.minus(p.qty*p.avg)
                MetricRow("P/L",FinancialFormatter.price(pl,p.currency))
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
            Row(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(12.dp)){
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
            Column(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(12.dp)){
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
        Text(label,color=RadarColors.TextMuted,fontSize=10.sp)
        Text(value,color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=10.sp)
    }
}
