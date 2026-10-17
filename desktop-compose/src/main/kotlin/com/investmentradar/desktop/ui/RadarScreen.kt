package com.investmentradar.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter
import com.investmentradar.desktop.model.RadarIntent
import com.investmentradar.desktop.model.RadarUiState

@Composable
fun RadarScreen(state:RadarUiState,onIntent:(RadarIntent)->Unit){
    Row(Modifier.fillMaxSize().background(RadarColors.Background)){
        WatchlistPanel(state.watchlist,state.selectedSymbol,{onIntent(RadarIntent.SelectTicker(it))},Modifier.width(292.dp).fillMaxHeight())
        Box(Modifier.width(1.dp).fillMaxHeight().background(RadarColors.Border))
        Column(Modifier.weight(1f).fillMaxHeight()){
            TopBar(state,{onIntent(RadarIntent.Refresh)})
            state.error?.let{
                Text(it,color=RadarColors.Negative,fontSize=10.sp,modifier=Modifier.padding(horizontal=16.dp,vertical=6.dp))
            }
            val asset=state.selected
            if(asset==null){
                Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                    Text(if(state.loading)"Loading market data…" else "Select an asset",color=RadarColors.TextMuted)
                }
            }else{
                AssetHeader(state)
                AreaLineChart(asset.history,asset.currency,Modifier.weight(1f).fillMaxWidth().padding(12.dp))
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(RadarColors.Border))
        Column(Modifier.width(310.dp).fillMaxHeight().background(RadarColors.Panel)){
            AllocationPanel(state,Modifier.weight(1f).fillMaxWidth())
            Box(Modifier.fillMaxWidth().height(1.dp).background(RadarColors.Border))
            DataPanel(state,Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun TopBar(state:RadarUiState,onRefresh:()->Unit){
    Row(
        Modifier.fillMaxWidth().height(54.dp).background(RadarColors.Panel).padding(horizontal=16.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Text("Investment Radar",color=RadarColors.Text,fontWeight=FontWeight.Bold,fontSize=13.sp)
        Spacer(Modifier.weight(1f))
        Text(state.status.mode.uppercase(),color=if(state.status.live)RadarColors.Positive else RadarColors.TextMuted,fontSize=9.sp)
        Spacer(Modifier.width(12.dp))
        TextButton(onClick=onRefresh){Text("Refresh",fontSize=10.sp)}
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
            Text("${a.symbol}  ·  ${a.sector?:"—"}",color=RadarColors.TextMuted,fontSize=10.sp)
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
private fun AllocationPanel(state:RadarUiState,modifier:Modifier){
    Column(modifier.background(RadarColors.Panel)){
        PanelTitle("PORTFOLIO","Asset allocation")
        AllocationDonut(state.allocation,Modifier.fillMaxSize())
    }
}

@Composable
private fun DataPanel(state:RadarUiState,modifier:Modifier){
    val a=state.selected
    Column(modifier.background(RadarColors.Panel)){
        PanelTitle("ANALYSIS","Data coverage")
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            KeyValue("Coverage",a?.let{"${"%.0f".format(it.coverage)}%"}?:"—")
            KeyValue("Score",a?.score?.let{"%.0f".format(it)}?:"—")
            KeyValue("Zone",a?.zone?:"—")
            KeyValue("Provider mode",state.status.mode)
            if(!state.status.live) Text("Live fundamentals depend on the configured provider plan.",color=RadarColors.TextMuted,fontSize=9.sp)
        }
    }
}

@Composable
private fun PanelTitle(kicker:String,title:String){
    Column(Modifier.fillMaxWidth().padding(14.dp)){
        Text(kicker,color=RadarColors.TextMuted,fontSize=8.sp)
        Text(title,color=RadarColors.Text,fontWeight=FontWeight.SemiBold,fontSize=12.sp)
    }
}

@Composable
private fun KeyValue(label:String,value:String){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
        Text(label,color=RadarColors.TextMuted,fontSize=10.sp)
        Text(value,color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=10.sp)
    }
}
