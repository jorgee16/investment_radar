package com.investmentradar.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.model.*

@Composable
fun ProvidersPage(state:RadarUiState,onIntent:(RadarIntent)->Unit){
    var demo by remember(state.providerSettings.demo){mutableStateOf(state.providerSettings.demo)}
    var eodhd by remember{mutableStateOf("")}
    var fmp by remember{mutableStateOf("")}
    var twelve by remember{mutableStateOf("")}
    var clearEodhd by remember{mutableStateOf(false)}
    var clearFmp by remember{mutableStateOf(false)}
    var clearTwelve by remember{mutableStateOf(false)}
    var testSymbol by remember{mutableStateOf("AAPL")}
    val routing=remember(state.status.routing){mutableStateMapOf<String,String>().apply{
        putAll(listOf("quotes","fundamentals","estimates","earnings","news","history").associateWith{state.status.routing[it]?:"auto"})
    }}

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        Text("DATA LAYER",color=RadarColors.TextMuted,fontSize=8.sp)
        Text("Providers & routing",color=RadarColors.Text,fontSize=18.sp)

        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            FilterChip(selected=demo,onClick={demo=true},label={Text("Demo")},enabled=!state.savingProviders)
            FilterChip(selected=!demo,onClick={demo=false},label={Text("Live")},enabled=!state.savingProviders)
            Text("Current: ${if(demo)"DEMO" else "LIVE"}",color=RadarColors.TextMuted,fontSize=10.sp)
        }

        ProviderKeyCard(
            title="EODHD",
            configured=state.providerSettings.eodhdConfigured,
            source=state.providerSettings.keySources["eodhd"].orEmpty(),
            value=eodhd,
            onValue={eodhd=it},
            clear=clearEodhd,
            onClear={clearEodhd=it}
        )
        ProviderKeyCard(
            title="Financial Modeling Prep",
            configured=state.providerSettings.fmpConfigured,
            source=state.providerSettings.keySources["fmp"].orEmpty(),
            value=fmp,
            onValue={fmp=it},
            clear=clearFmp,
            onClear={clearFmp=it}
        )
        ProviderKeyCard(
            title="Twelve Data",
            configured=state.providerSettings.twelveConfigured,
            source=state.providerSettings.keySources["twelve"].orEmpty(),
            value=twelve,
            onValue={twelve=it},
            clear=clearTwelve,
            onClear={clearTwelve=it}
        )

        Text("ROUTING",color=RadarColors.TextMuted,fontSize=8.sp)
        listOf("quotes","fundamentals","estimates","earnings","news","history").forEach{cap->
            val allowed=listOf("auto")+state.providers.filter{it.id!="demo"&&cap in it.capabilities}.map{it.id}
            RoutingRow(cap,routing[cap]?:"auto",allowed){routing[cap]=it}
        }

        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(
                onClick={
                    onIntent(
                        RadarIntent.SaveProviders(
                            demo=demo,
                            eodhdKey=if(clearEodhd)"" else eodhd.trim().ifEmpty{null},
                            fmpKey=if(clearFmp)"" else fmp.trim().ifEmpty{null},
                            twelveKey=if(clearTwelve)"" else twelve.trim().ifEmpty{null},
                            routing=routing.toMap()
                        )
                    )
                    eodhd="";fmp="";twelve=""
                    clearEodhd=false;clearFmp=false;clearTwelve=false
                },
                enabled=!state.savingProviders
            ){Text(if(state.savingProviders)"Saving…" else "Save configuration")}
        }

        HorizontalDivider(color=RadarColors.Border)
        Text("CONNECTION TEST",color=RadarColors.TextMuted,fontSize=8.sp)
        OutlinedTextField(testSymbol,{testSymbol=it.uppercase()},label={Text("Test symbol")},singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={onIntent(RadarIntent.TestProvider("all",testSymbol))}){Text("Test all")}
            state.providers.filter{it.id!="demo"}.forEach{p->
                OutlinedButton(onClick={onIntent(RadarIntent.TestProvider(p.id,testSymbol))}){Text(p.name)}
            }
        }

        state.providers.forEach{p->
            val report=state.providerTests[p.id]
            Column(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(12.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text(p.name,color=RadarColors.Text,fontSize=11.sp)
                    Text(
                        when{
                            report!=null -> report.code
                            p.connected -> "connected"
                            p.enabled -> "configured"
                            else -> "no key"
                        },
                        color=when{
                            report?.connected==true -> RadarColors.Positive
                            report!=null -> RadarColors.Negative
                            else -> RadarColors.TextMuted
                        },
                        fontSize=9.sp
                    )
                }
                Text(report?.message?:p.status,color=RadarColors.TextMuted,fontSize=9.sp)
                Text(p.capabilities.joinToString(" · "),color=RadarColors.TextMuted,fontSize=8.sp)
            }
        }
    }
}

@Composable
private fun ProviderKeyCard(
    title:String,
    configured:Boolean,
    source:String,
    value:String,
    onValue:(String)->Unit,
    clear:Boolean,
    onClear:(Boolean)->Unit
){
    Column(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(title,color=RadarColors.Text,fontSize=11.sp)
            Text(if(configured)"configured · $source" else "not configured",color=if(configured)RadarColors.Positive else RadarColors.TextMuted,fontSize=9.sp)
        }
        OutlinedTextField(
            value=value,
            onValueChange=onValue,
            modifier=Modifier.fillMaxWidth(),
            label={Text(if(configured)"New key (leave blank to keep current)" else "API key")},
            visualTransformation=PasswordVisualTransformation(),
            singleLine=true
        )
        Row{
            Checkbox(checked=clear,onCheckedChange=onClear)
            Text("Remove stored key",color=RadarColors.TextMuted,fontSize=9.sp,modifier=Modifier.padding(top=12.dp))
        }
    }
}

@Composable
private fun RoutingRow(capability:String,value:String,options:List<String>,onValue:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(10.dp),horizontalArrangement=Arrangement.SpaceBetween){
        Text(capability,color=RadarColors.Text,fontSize=10.sp)
        Box{
            TextButton(onClick={open=true}){Text(value,fontSize=10.sp)}
            DropdownMenu(expanded=open,onDismissRequest={open=false}){
                options.distinct().forEach{candidate->
                    DropdownMenuItem(text={Text(candidate)},onClick={onValue(candidate);open=false})
                }
            }
        }
    }
}
