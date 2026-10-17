package com.investmentradar.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter
import com.investmentradar.desktop.model.*

@Composable
fun WatchlistPage(state:RadarUiState,onIntent:(RadarIntent)->Unit){
    var adding by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Column{
                Text("IDEAS",color=RadarColors.TextMuted,fontSize=8.sp)
                Text("Watchlist",color=RadarColors.Text,fontSize=18.sp)
            }
            Button(onClick={adding=true}){Text("+ Ticker")}
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)){
            state.watchlist.forEach{w->
                Row(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(10.dp)){
                    TextButton(onClick={onIntent(RadarIntent.SelectTicker(w.symbol))},modifier=Modifier.width(120.dp)){Text(w.symbol)}
                    Text(w.name,color=RadarColors.TextMuted,fontSize=9.sp,modifier=Modifier.weight(1f))
                    Text(FinancialFormatter.price(w.price,w.currency),color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.width(120.dp))
                    Text(FinancialFormatter.percent(w.changePct),color=if((w.changePct?:0.0)>=0)RadarColors.Positive else RadarColors.Negative,fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.width(80.dp))
                    Text(w.score?.let{"%.0f".format(it)}?:"—",color=RadarColors.Accent,fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.width(50.dp))
                    TextButton(onClick={onIntent(RadarIntent.RemoveWatch(w.symbol))}){Text("Remove",fontSize=9.sp)}
                }
            }
        }
    }
    if(adding){
        var symbol by remember{mutableStateOf("")}
        AlertDialog(
            onDismissRequest={adding=false},
            title={Text("Add ticker")},
            text={OutlinedTextField(symbol,{symbol=it.uppercase()},label={Text("Ticker")},singleLine=true)},
            confirmButton={Button(onClick={if(symbol.isNotBlank())onIntent(RadarIntent.AddWatch(symbol));adding=false}){Text("Add")}},
            dismissButton={TextButton(onClick={adding=false}){Text("Cancel")}}
        )
    }
}

@Composable
fun ScreenerPage(state:RadarUiState,onIntent:(RadarIntent)->Unit){
    var minScore by remember{mutableFloatStateOf(70f)}
    var zone by remember{mutableStateOf("all")}
    val rows=state.watchlist.filter{(it.score?:-1.0)>=minScore&&(zone=="all"||it.zone==zone)}
    Column(Modifier.fillMaxSize().padding(16.dp)){
        Text("DISCOVERY",color=RadarColors.TextMuted,fontSize=8.sp)
        Text("Screener",color=RadarColors.Text,fontSize=18.sp)
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Minimum score ${minScore.toInt()}",color=RadarColors.TextMuted,fontSize=10.sp)
            Slider(minScore,{minScore=it},valueRange=40f..90f,modifier=Modifier.width(260.dp))
            listOf("all","BUY","FAIR","EXPENSIVE").forEach{z->
                FilterChip(selected=zone==z,onClick={zone=z},label={Text(z)})
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
            rows.forEach{w->
                Row(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(10.dp)){
                    TextButton(onClick={onIntent(RadarIntent.SelectTicker(w.symbol))}){Text(w.symbol)}
                    Spacer(Modifier.weight(1f))
                    Text(w.zone,color=RadarColors.TextMuted,fontSize=10.sp)
                    Spacer(Modifier.width(20.dp))
                    Text(w.score?.let{"%.0f".format(it)}?:"—",color=RadarColors.Accent,fontFamily=FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun EventsPage(state:RadarUiState){
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text("CATALYSTS",color=RadarColors.TextMuted,fontSize=8.sp)
        Text("Events",color=RadarColors.Text,fontSize=18.sp)
        state.earnings.forEach{e->
            Row(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(10.dp)){
                Text(e.date,color=RadarColors.TextMuted,fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.width(110.dp))
                Text(e.symbol,color=RadarColors.Text,fontSize=10.sp,modifier=Modifier.width(90.dp))
                Column{
                    Text(e.kind,color=RadarColors.Text,fontSize=10.sp)
                    Text("${e.note} · ${e.source}",color=RadarColors.TextMuted,fontSize=9.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsPage(state:RadarUiState){
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("APPLICATION",color=RadarColors.TextMuted,fontSize=8.sp)
        Text("Settings",color=RadarColors.Text,fontSize=18.sp)
        Column(Modifier.width(520.dp).background(RadarColors.Panel).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            MetricRow("Runtime","Kotlin Compose Desktop")
            MetricRow("Engine",state.status.engine.ifBlank{"C++20"})
            MetricRow("Data mode",state.status.mode.uppercase())
            MetricRow("Providers",if(state.status.configured)"configured" else "not configured")
            MetricRow("Connection",if(state.status.live)"live" else if(state.status.mode=="demo")"demo" else "not validated")
        }
    }
}
