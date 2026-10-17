package com.investmentradar.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.investmentradar.desktop.model.*

private enum class HoldingsSort(val label:String){ SYMBOL("Ticker"), VALUE("Market value"), RETURN("Return %") }

@Composable fun PortfolioPage(state:RadarUiState,onIntent:(RadarIntent)->Unit){
 var adding by remember{mutableStateOf(false)}
 var sort by remember{mutableStateOf(HoldingsSort.SYMBOL)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   Text("Portfolio",style=MaterialTheme.typography.headlineSmall)
   Button(onClick={adding=true}){Text("+ Position")}
  }
  if(state.portfolio.isEmpty())Text("No positions yet. Add a holding to track its return.")
  else {
   Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
    HoldingsSort.entries.forEach{option->
     FilterChip(selected=sort==option,onClick={sort=option},label={Text(option.label)})
    }
   }
   Text("Positions are grouped by quote currency. Sorting never mixes EUR and USD values.",
    style=MaterialTheme.typography.labelSmall)
  }
  val indexed=state.portfolio.withIndex().toList()
  indexed.groupBy{it.value.currency}.toSortedMap().forEach{(currency,rows)->
   PortfolioSummary(currency,rows.map{it.value},state.portfolioQuotes)
   val ordered=when(sort){
    HoldingsSort.SYMBOL -> rows.sortedBy{it.value.symbol}
    HoldingsSort.VALUE -> rows.sortedByDescending{item->
     state.portfolioQuotes[item.value.symbol]?.takeIf{it.currency==currency}?.price?.times(item.value.qty) ?: Double.NEGATIVE_INFINITY
    }
    HoldingsSort.RETURN -> rows.sortedByDescending{item->
     state.portfolioQuotes[item.value.symbol]?.takeIf{it.currency==currency}?.price?.let{
      price->(price/item.value.avg-1.0)*100.0
     } ?: Double.NEGATIVE_INFINITY
    }
   }
   ordered.forEach{item->
    PortfolioHolding(item.value,state.portfolioQuotes[item.value.symbol]){
     onIntent(RadarIntent.RemovePosition(item.index))
    }
   }
  }
 }
 if(adding)AddPositionDialog(onDismiss={adding=false},onAdd={onIntent(RadarIntent.AddPosition(it));adding=false})
}
