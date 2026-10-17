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
 val invested=positions.sumOf{it.qty*it.avg}
 val known=positions.all{p->quotes[p.symbol]?.let{it.price!=null&&it.currency==currency}==true}
 val value=if(known)positions.sumOf{it.qty*(quotes[it.symbol]?.price?:0.0)} else null
 val gain=value?.minus(invested)
 Column(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(16.dp)){
  Text("$currency · ${positions.size} holdings",color=RadarColors.TextMuted,fontSize=10.sp)
  Text(F.price(value,currency),color=RadarColors.Text,fontSize=24.sp)
  Text("Invested ${F.price(invested,currency)}",color=RadarColors.TextMuted,fontSize=11.sp)
  Text("P/L ${F.price(gain,currency)} · ${F.percent(gain?.div(invested)?.times(100))}",
   color=if(gain==null)RadarColors.TextMuted else if(gain>=0)RadarColors.Positive else RadarColors.Negative,fontSize=14.sp)
  if(!known)Text("Incomplete quotes or currency mismatch · no FX conversion",color=RadarColors.TextMuted,fontSize=10.sp)
 }
}
