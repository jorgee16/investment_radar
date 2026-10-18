package com.investmentradar.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.format.FinancialFormatter
import com.investmentradar.desktop.model.WatchItem

@Composable
fun WatchlistPanel(
    items:List<WatchItem>,
    selected:String,
    onSelect:(String)->Unit,
    modifier:Modifier=Modifier
){
    Column(modifier.background(RadarColors.Panel)){
        Column(Modifier.padding(16.dp)){
            Text("INVESTMENT RADAR",fontWeight=FontWeight.Bold,fontSize=13.sp,color=RadarColors.Text)
            Text("Watchlist",fontSize=10.sp,color=RadarColors.TextMuted)
        }
        LazyColumn{
            items(items,key={it.symbol}){item->
                TickerRow(item,item.symbol==selected,{onSelect(item.symbol)})
            }
        }
    }
}

@Composable
private fun TickerRow(item:WatchItem, selected:Boolean, onClick:()->Unit){
    val changeColor=when{
        item.changePct==null->RadarColors.TextMuted
        item.changePct>=0->RadarColors.Positive
        else->RadarColors.Negative
    }
    Row(
        Modifier.fillMaxWidth().height(64.dp)
            .background(if(selected) RadarColors.Selected else RadarColors.Panel)
            .clickable(onClick=onClick).padding(horizontal=12.dp,vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Column(Modifier.width(76.dp)){
            Text(item.symbol,color=RadarColors.Text,fontWeight=FontWeight.SemiBold,fontSize=11.sp)
            Text(item.name,color=RadarColors.TextMuted,fontSize=9.sp,maxLines=1)
        }
        Sparkline(item.sparkline,(item.changePct?:0.0)>=0,Modifier.weight(1f).height(30.dp).padding(horizontal=8.dp))
        Column(horizontalAlignment=Alignment.End,modifier=Modifier.width(94.dp)){
            Text(FinancialFormatter.price(item.price,item.currency),color=RadarColors.Text,fontFamily=FontFamily.Monospace,fontSize=10.sp)
            Text(FinancialFormatter.percent(item.changePct),color=changeColor,fontFamily=FontFamily.Monospace,fontSize=10.sp)
        }
    }
}
