package com.investmentradar.desktop.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.investmentradar.desktop.model.*

@Composable fun RadarScreen(state:RadarUiState,onIntent:(RadarIntent)->Unit){
 BoxWithConstraints(Modifier.fillMaxSize()){
  val compact=maxWidth<1100.dp
  Row(Modifier.fillMaxSize().background(RadarColors.Background)){
   AppNavigation(state.page,onIntent,compact)
   Column(Modifier.weight(1f).fillMaxHeight()){
    AppTopBar(state,onIntent,compact)
    state.error?.takeIf{it.isNotBlank()}?.let{Text(it,color=RadarColors.Negative)}
    when(state.page){
     AppPage.DASHBOARD -> DashboardPage(state,onIntent)
     AppPage.RADAR -> Row(Modifier.fillMaxSize()){
      if(!compact){
       WatchlistPanel(state.watchlist,state.selectedSymbol,{onIntent(RadarIntent.SelectTicker(it))},Modifier.width(280.dp).fillMaxHeight())
       Box(Modifier.width(1.dp).fillMaxHeight().background(RadarColors.Border))
      }
      AssetWorkspace(state,onIntent,Modifier.weight(1f).fillMaxHeight())
     }
     AppPage.PORTFOLIO -> PortfolioPage(state,onIntent)
     AppPage.WATCHLIST -> WatchlistPage(state,onIntent)
     AppPage.SCREENER -> ScreenerPage(state,onIntent)
     AppPage.EVENTS -> EventsPage(state)
     AppPage.PROVIDERS -> ProvidersPage(state,onIntent)
     AppPage.SETTINGS -> SettingsPage(state)
    }
   }
  }
 }
}
