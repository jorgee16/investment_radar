package com.investmentradar.desktop.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.model.*

@Composable fun AppTopBar(state:RadarUiState,onIntent:(RadarIntent)->Unit,compact:Boolean){
 var search by remember(state.selectedSymbol){mutableStateOf(state.selectedSymbol)}
 Row(Modifier.fillMaxWidth().height(58.dp).background(RadarColors.Panel).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically){
  Text(when(state.page){
   AppPage.DASHBOARD->"Dashboard";AppPage.RADAR->"Asset analysis";AppPage.PORTFOLIO->"Portfolio"
   AppPage.WATCHLIST->"Watchlist";AppPage.SCREENER->"Screener";AppPage.EVENTS->"Events"
   AppPage.PROVIDERS->"Providers";AppPage.SETTINGS->"Settings"
  },color=RadarColors.Text,fontWeight=FontWeight.Bold,fontSize=13.sp)
  Spacer(Modifier.weight(1f))
  if(state.page==AppPage.RADAR||state.page==AppPage.DASHBOARD){
   OutlinedTextField(value=search,onValueChange={search=it.uppercase()},
    modifier=Modifier.width(if(compact)140.dp else 240.dp),singleLine=true,
    textStyle=LocalTextStyle.current.copy(fontSize=10.sp),
    placeholder={Text("Ticker",fontSize=9.sp)},
    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
    keyboardActions=KeyboardActions(onSearch={onIntent(RadarIntent.SelectTicker(search))}))
   Spacer(Modifier.width(10.dp))
  }
  if(!compact)Text(if(state.status.mode=="demo")"DEMO" else if(state.status.live)"LIVE" else "UNVERIFIED",
   color=if(state.status.live)RadarColors.Positive else RadarColors.TextMuted,fontSize=9.sp)
  TextButton(onClick={onIntent(RadarIntent.Refresh)}){Text("Refresh",fontSize=10.sp)}
 }
}
