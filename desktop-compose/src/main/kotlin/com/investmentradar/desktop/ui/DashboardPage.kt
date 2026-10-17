package com.investmentradar.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter
import com.investmentradar.desktop.model.*

@Composable
fun DashboardPage(state:RadarUiState,onIntent:(RadarIntent)->Unit){
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        Text("INVESTMENT INTELLIGENCE",color=RadarColors.TextMuted,fontSize=8.sp)
        Text("Dashboard",color=RadarColors.Text,fontSize=20.sp,fontWeight=FontWeight.Bold)

        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
            SummaryCard("Watchlist",state.watchSymbols.size.toString(),"tracked assets",Modifier.weight(1f))
            SummaryCard("Portfolio",state.portfolio.size.toString(),"positions",Modifier.weight(1f))
            SummaryCard(
                "Data",
                state.status.mode.uppercase(),
                if(state.status.live)"live providers validated" else if(state.status.mode=="demo")"synthetic demo mode" else "provider setup required",
                Modifier.weight(1f)
            )
            val next=state.earnings.firstOrNull()
            SummaryCard("Next event",next?.symbol?:"—",next?.date?:"no event loaded",Modifier.weight(1f))
        }

        Row(Modifier.weight(1f).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            PanelSurface(Modifier.weight(2f).fillMaxHeight()){ Column{
                Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){
                    Column{
                        Text("WATCHLIST",color=RadarColors.TextMuted,fontSize=8.sp)
                        Text("Market overview",color=RadarColors.Text,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    }
                    TextButton(onClick={onIntent(RadarIntent.Navigate(AppPage.WATCHLIST))}){Text("Open")}
                }
                state.watchlist.take(8).forEach{w->
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=7.dp)){
                        TextButton(onClick={onIntent(RadarIntent.SelectTicker(w.symbol))},modifier=Modifier.width(100.dp)){Text(w.symbol,fontSize=10.sp)}
                        Sparkline(w.sparkline,(w.changePct?:0.0)>=0,Modifier.weight(1f).height(26.dp).padding(horizontal=10.dp))
                        Text(FinancialFormatter.price(w.price,w.currency),color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.width(110.dp))
                        Text(
                            FinancialFormatter.percent(w.changePct),
                            color=if((w.changePct?:0.0)>=0)RadarColors.Positive else RadarColors.Negative,
                            fontFamily=FontFamily.Monospace,fontSize=10.sp,modifier=Modifier.width(70.dp)
                        )
                    }
                }
            }}
            Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(12.dp)){
                PanelSurface(Modifier.weight(1f).fillMaxWidth()){ Column(Modifier.fillMaxSize().padding(12.dp)){
                    Text("PORTFOLIO EXPOSURE",color=RadarColors.TextMuted,fontSize=8.sp)
                    AllocationDonut(state.allocation,Modifier.fillMaxSize())
                }}
                PanelSurface(Modifier.weight(1f).fillMaxWidth()){ Column(Modifier.fillMaxSize().padding(12.dp)){
                    Text("DATA HEALTH",color=RadarColors.TextMuted,fontSize=8.sp)
                    Spacer(Modifier.height(10.dp))
                    MetricRow("Mode",state.status.mode.uppercase())
                    MetricRow("Configured",if(state.status.configured)"yes" else "no")
                    MetricRow("Connection",if(state.status.live)"validated" else "not validated")
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick={onIntent(RadarIntent.Navigate(AppPage.PROVIDERS))}){Text("Configure providers",fontSize=9.sp)}
                }}
            }
        }
    }
}

@Composable
private fun SummaryCard(title:String,value:String,detail:String,modifier:Modifier=Modifier){
    PanelSurface(modifier){ Column(Modifier.fillMaxWidth().padding(12.dp)){
        Text(title.uppercase(),color=RadarColors.TextMuted,fontSize=8.sp)
        Text(value,color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=18.sp,fontWeight=FontWeight.Bold)
        Text(detail,color=RadarColors.TextMuted,fontSize=8.sp)
    }}
}
