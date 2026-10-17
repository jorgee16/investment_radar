package com.investmentradar.desktop.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.investmentradar.desktop.model.*

@Composable fun PortfolioPage(state:RadarUiState,onIntent:(RadarIntent)->Unit){
 var adding by remember{mutableStateOf(false)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   Text("Portfolio",style=MaterialTheme.typography.headlineSmall)
   Button(onClick={adding=true}){Text("+ Position")}
  }
  if(state.portfolio.isEmpty())Text("No positions yet. Add a holding to track its return.")
  state.portfolio.groupBy{it.currency}.forEach{(currency,rows)->PortfolioSummary(currency,rows,state.portfolioQuotes)}
  state.portfolio.forEachIndexed{i,p->PortfolioHolding(p,state.portfolioQuotes[p.symbol]){onIntent(RadarIntent.RemovePosition(i))}}
 }
 if(adding)AddPositionDialog(onDismiss={adding=false},onAdd={onIntent(RadarIntent.AddPosition(it));adding=false})
}
