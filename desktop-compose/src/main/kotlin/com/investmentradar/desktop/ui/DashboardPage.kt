package com.investmentradar.desktop.ui

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
import com.investmentradar.desktop.format.FinancialFormatter as F
import com.investmentradar.desktop.model.*
import java.time.Instant

@Composable
fun DashboardPage(state:RadarUiState,onIntent:(RadarIntent)->Unit){
    BoxWithConstraints(Modifier.fillMaxSize()){
        val compact=maxWidth<850.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("INVESTMENT INTELLIGENCE",color=RadarColors.TextMuted,fontSize=9.sp)
            Text("Portfolio overview",color=RadarColors.Text,fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text("Market-value returns from available quotes; never sum currencies without FX conversion.",
                color=RadarColors.TextMuted,fontSize=10.sp)
            if(state.portfolio.isEmpty()){
                PanelSurface(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text("No holdings yet",color=RadarColors.Text,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                        Text("Add quantity, average cost and quote currency to track your P/L.",
                            color=RadarColors.TextMuted,fontSize=11.sp)
                        TextButton(onClick={onIntent(RadarIntent.Navigate(AppPage.PORTFOLIO))}){Text("Open portfolio →")}
                    }
                }
            }else{
                state.portfolio.groupBy{it.currency}.forEach{(currency,positions)->
                    val invested=positions.sumOf{it.qty*it.avg}
                    val complete=positions.all{p->
                        state.portfolioQuotes[p.symbol]?.let{q->q.price!=null&&q.currency==currency}==true
                    }
                    val current=if(complete)positions.sumOf{p->
                        p.qty*(state.portfolioQuotes[p.symbol]?.price?:0.0)
                    }else null
                    val gain=current?.minus(invested)
                    val gainPct=if(invested>0.0)gain?.div(invested)?.times(100.0) else null
                    val times=positions.mapNotNull{state.portfolioQuotes[it.symbol]?.timestamp}
                    val oldest=if(times.size==positions.size)times.minOrNull() else null
                    val stale=oldest!=null && System.currentTimeMillis()/1000-oldest>86400
                    PanelSurface(Modifier.fillMaxWidth()){
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                Column{
                                    Text(currency+" PORTFOLIO",color=RadarColors.TextMuted,fontSize=9.sp)
                                    Text(F.price(current,currency),color=RadarColors.Text,
                                        fontFamily=FontFamily.Monospace,fontSize=26.sp,fontWeight=FontWeight.Bold)
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick={onIntent(RadarIntent.Navigate(AppPage.PORTFOLIO))}){Text("Holdings →")}
                            }
                            if(compact){
                                DashboardNumber("Invested",F.price(invested,currency))
                                DashboardNumber("Unrealized P/L",F.price(gain,currency)+"  "+F.percent(gainPct),gain)
                            }else{
                                Row(horizontalArrangement=Arrangement.spacedBy(32.dp)){
                                    DashboardNumber("Invested",F.price(invested,currency))
                                    DashboardNumber("Unrealized P/L",F.price(gain,currency)+"  "+F.percent(gainPct),gain)
                                }
                            }
                            val provenance=if(oldest==null)"Quote time not provided"
                                else "Oldest quote: "+Instant.ofEpochSecond(oldest).toString().take(16).replace("T"," ")+" UTC"
                            Text(provenance+(if(stale)" · STALE" else "")+
                                (if(state.status.mode=="demo")" · DEMO (synthetic)" else ""),
                                color=if(stale)RadarColors.Negative else RadarColors.TextMuted,fontSize=9.sp)
                            if(!complete)Text("Incomplete or currency-mismatched quotes: P/L withheld. No assumed FX conversion.",
                                color=RadarColors.Negative,fontSize=10.sp)
                        }
                    }
                }
            }
            val contributors=state.portfolio.mapNotNull{p->
                val q=state.portfolioQuotes[p.symbol]
                if(q?.price!=null&&q.currency==p.currency)
                    Triple(p.symbol,(q.price-p.avg)*p.qty,p.currency)
                else null
            }.sortedByDescending{it.second}
            if(contributors.isNotEmpty()){
                PanelSurface(Modifier.fillMaxWidth()){
                    Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                        Text("HOLDING CONTRIBUTORS",color=RadarColors.TextMuted,fontSize=9.sp)
                        contributors.take(5).forEach{(symbol,gain,currency)->
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                TextButton(onClick={onIntent(RadarIntent.SelectTicker(symbol))}){Text(symbol,fontSize=11.sp)}
                                Spacer(Modifier.weight(1f))
                                Text(F.price(gain,currency),fontFamily=FontFamily.Monospace,
                                    color=if(gain>=0)RadarColors.Positive else RadarColors.Negative,fontSize=11.sp)
                            }
                        }
                        Text("Unrealized gains/losses; excludes realized trades, dividends and FX.",
                            color=RadarColors.TextMuted,fontSize=9.sp)
                    }
                }
            }
            if(compact){
                DashboardWatch(state,onIntent,Modifier.fillMaxWidth())
                DashboardHealth(state,onIntent,Modifier.fillMaxWidth())
            }else{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    DashboardWatch(state,onIntent,Modifier.weight(2f))
                    DashboardHealth(state,onIntent,Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DashboardNumber(label:String,value:String,gain:Double?=null){
    Column{
        Text(label.uppercase(),color=RadarColors.TextMuted,fontSize=9.sp)
        Text(value,color=if(gain==null)RadarColors.Text else if(gain>=0)RadarColors.Positive else RadarColors.Negative,
            fontFamily=FontFamily.Monospace,fontSize=15.sp,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun DashboardWatch(state:RadarUiState,onIntent:(RadarIntent)->Unit,modifier:Modifier){
    PanelSurface(modifier){
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column{
                    Text("WATCHLIST",color=RadarColors.TextMuted,fontSize=9.sp)
                    Text(state.watchSymbols.size.toString()+" tracked assets",color=RadarColors.Text,fontSize=13.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick={onIntent(RadarIntent.Navigate(AppPage.WATCHLIST))}){Text("Manage →")}
            }
            if(state.watchSymbols.isEmpty())Text("No tickers tracked. Add one in Watchlist.",color=RadarColors.TextMuted,fontSize=10.sp)
            state.watchlist.take(6).forEach{w->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick={onIntent(RadarIntent.SelectTicker(w.symbol))},modifier=Modifier.width(105.dp)){
                        Text(w.symbol,fontSize=10.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(F.price(w.price,w.currency),color=RadarColors.Text,
                        fontFamily=FontFamily.Monospace,fontSize=10.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(F.percent(w.changePct),fontFamily=FontFamily.Monospace,fontSize=10.sp,
                        color=if(w.changePct==null)RadarColors.TextMuted else if(w.changePct>=0)RadarColors.Positive else RadarColors.Negative)
                }
            }
        }
    }
}

@Composable
private fun DashboardHealth(state:RadarUiState,onIntent:(RadarIntent)->Unit,modifier:Modifier){
    PanelSurface(modifier){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("DATA & EVENTS",color=RadarColors.TextMuted,fontSize=9.sp)
            MetricRow("Mode",state.status.mode.uppercase())
            MetricRow("Provider",if(state.status.live)"Validated" else if(state.status.mode=="demo")"Synthetic demo" else "Unverified")
            MetricRow("Next event",state.earnings.firstOrNull()?.let{it.symbol+" · "+it.date}?:"—")
            TextButton(onClick={onIntent(RadarIntent.Navigate(AppPage.PROVIDERS))}){Text("Configure providers →")}
        }
    }
}
