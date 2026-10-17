package com.investmentradar.desktop.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter as F
import com.investmentradar.desktop.model.*

@Composable fun PortfolioHolding(p:PortfolioPosition,q:QuoteDto?,remove:()->Unit){
 val value=if(q?.price!=null&&q.currency==p.currency)p.qty*q.price else null
 val gain=value?.minus(p.qty*p.avg)
 val change=q?.changePct
 val day=if(value!=null&&change!=null&&change> -100.0)
  value*change/(100.0+change) else null
 Column(Modifier.fillMaxWidth().background(RadarColors.Panel).padding(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   Text(p.symbol,color=RadarColors.Text,fontSize=13.sp)
   Text(F.price(gain,p.currency),color=if((gain?:0.0)>=0)RadarColors.Positive else RadarColors.Negative)
  }
  Text("${p.qty} × ${F.price(p.avg,p.currency)} · Value ${F.price(value,p.currency)}",color=RadarColors.TextMuted,fontSize=10.sp)
  Text("Return ${F.percent(gain?.div(p.qty*p.avg)?.times(100))} · Day ${F.price(day,p.currency)}",color=RadarColors.TextMuted,fontSize=10.sp)
  Text("${q?.source?:"No quote"} · ${if(value==null)"Missing or mismatched currency" else "Market quote"}",color=RadarColors.TextMuted,fontSize=9.sp)
  TextButton(onClick=remove){Text("Remove",fontSize=10.sp)}
 }
}
