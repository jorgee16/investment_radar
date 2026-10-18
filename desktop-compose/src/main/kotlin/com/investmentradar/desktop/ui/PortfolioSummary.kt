package com.investmentradar.desktop.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter as F
import com.investmentradar.desktop.model.*

@Composable fun PortfolioSummary(currency:String,positions:List<PortfolioPosition>,quotes:Map<String,QuoteDto>){
 val totals=portfolioCurrencyPerformance(currency,positions,quotes)
 Column(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(16.dp)){
  Text("$currency · ${positions.size} holdings",color=RadarColors.TextMuted,fontSize=10.sp)
  Text(F.price(totals.marketValue,currency),color=RadarColors.Text,fontSize=24.sp)
  Text("Invested ${F.price(totals.invested,currency)}",color=RadarColors.TextMuted,fontSize=11.sp)
  Text("Unrealized P/L ${F.price(totals.unrealized,currency)} · ${F.percent(totals.returnPercent)}",
   color=if(totals.unrealized==null)RadarColors.TextMuted else if(totals.unrealized>=0)RadarColors.Positive else RadarColors.Negative,fontSize=14.sp)
  Text("Session change (indicative) ${F.price(totals.indicativeSessionChange,currency)}",
   color=if(totals.indicativeSessionChange==null)RadarColors.TextMuted else if(totals.indicativeSessionChange>=0)RadarColors.Positive else RadarColors.Negative,fontSize=12.sp)
  if(!totals.complete)Text("Incomplete quotes or currency mismatch · no FX conversion",color=RadarColors.TextMuted,fontSize=10.sp)
  else if(!totals.sessionChangeComplete)Text("Session estimate unavailable: missing provider % change",color=RadarColors.TextMuted,fontSize=10.sp)
  Text("Average-cost approximation; excludes realized trades, fees, dividends and FX.",
   color=RadarColors.TextMuted,fontSize=9.sp)
 }
}
