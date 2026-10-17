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
fun PortfolioPage(state:RadarUiState,onIntent:(RadarIntent)->Unit){
    var adding by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Column{
                Text("YOUR MONEY",color=RadarColors.TextMuted,fontSize=8.sp)
                Text("Portfolio",color=RadarColors.Text,fontSize=18.sp)
            }
            Button(onClick={adding=true}){Text("+ Position")}
        }
        Row(Modifier.height(220.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Column(Modifier.weight(1f).fillMaxHeight().background(RadarColors.Panel)){
                Text("PORTFOLIO EXPOSURE",color=RadarColors.TextMuted,fontSize=8.sp,modifier=Modifier.padding(12.dp))
                AllocationDonut(state.allocation,Modifier.fillMaxSize())
            }
            Column(Modifier.weight(2f).fillMaxHeight().background(RadarColors.Panel).padding(12.dp)){
                Text("SUMMARY",color=RadarColors.TextMuted,fontSize=8.sp)
                state.portfolio.groupBy{it.currency}.forEach{(currency,positions)->
                    val invested=positions.sumOf{it.qty*it.avg}
                    val currentKnown=positions.all{p->
                        val q=state.portfolioQuotes[p.symbol]
                        q?.price!=null&&q.currency==p.currency
                    }
                    val current=if(currentKnown)positions.sumOf{p->
                        p.qty*(state.portfolioQuotes[p.symbol]?.price?:0.0)
                    } else null
                    MetricRow("$currency invested",FinancialFormatter.price(invested,currency))
                    MetricRow("$currency current",FinancialFormatter.price(current,currency))
                    MetricRow("$currency P/L",FinancialFormatter.price(current?.minus(invested),currency))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)){
            if(state.portfolio.isEmpty()) Text("No positions yet",color=RadarColors.TextMuted)
            state.portfolio.forEachIndexed{i,p->
                val quote=state.portfolioQuotes[p.symbol]
                val current=if(quote?.price!=null&&quote.currency==p.currency)p.qty*quote.price else null
                val invested=p.qty*p.avg
                val pl=current?.minus(invested)
                Row(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(10.dp)){
                    Column(Modifier.width(140.dp)){
                        Text(p.symbol,color=RadarColors.Text,fontSize=10.sp)
                        Text("${p.qty} units",color=RadarColors.TextMuted,fontSize=9.sp)
                    }
                    Text(FinancialFormatter.price(p.avg,p.currency),color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.width(120.dp))
                    Text(FinancialFormatter.price(current,p.currency),color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.width(120.dp))
                    Text(FinancialFormatter.price(pl,p.currency),color=if((pl?:0.0)>=0)RadarColors.Positive else RadarColors.Negative,fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.weight(1f))
                    TextButton(onClick={onIntent(RadarIntent.RemovePosition(i))}){Text("Remove",fontSize=9.sp)}
                }
            }
        }
    }
    if(adding){
        AddPositionDialog(
            onDismiss={adding=false},
            onAdd={onIntent(RadarIntent.AddPosition(it));adding=false}
        )
    }
}

@Composable
private fun AddPositionDialog(onDismiss:()->Unit,onAdd:(PortfolioPosition)->Unit){
    var symbol by remember{mutableStateOf("")}
    var qty by remember{mutableStateOf("")}
    var avg by remember{mutableStateOf("")}
    var currency by remember{mutableStateOf("EUR")}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("Add position")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(symbol,{symbol=it.uppercase()},label={Text("Ticker")},singleLine=true)
                OutlinedTextField(qty,{qty=it},label={Text("Quantity")},singleLine=true)
                OutlinedTextField(avg,{avg=it},label={Text("Average price")},singleLine=true)
                OutlinedTextField(currency,{currency=it.uppercase()},label={Text("Currency")},singleLine=true)
            }
        },
        confirmButton={
            Button(onClick={
                val q=qty.toDoubleOrNull()
                val a=avg.toDoubleOrNull()
                if(symbol.isNotBlank()&&q!=null&&a!=null&&q>0&&a>0)onAdd(PortfolioPosition(symbol,q,a,currency))
            }){Text("Add")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}}
    )
}
