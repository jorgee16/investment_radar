package com.investmentradar.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.model.*

@Composable
fun RadarScreen(state:RadarUiState,onIntent:(RadarIntent)->Unit){
    Row(Modifier.fillMaxSize().background(RadarColors.Background)){
        AppNavigation(state.page,onIntent)
        Box(Modifier.width(1.dp).fillMaxHeight().background(RadarColors.Border))
        Column(Modifier.weight(1f).fillMaxHeight()){
            AppTopBar(state,onIntent)
            state.error?.takeIf{it.isNotBlank()}?.let{
                Text(
                    it,
                    color=RadarColors.Negative,
                    fontSize=10.sp,
                    modifier=Modifier.fillMaxWidth().background(RadarColors.Panel).padding(horizontal=14.dp,vertical=7.dp)
                )
            }
            when(state.page){
                AppPage.DASHBOARD -> DashboardPage(state,onIntent)
                AppPage.RADAR -> Row(Modifier.fillMaxSize()){
                    WatchlistPanel(
                        state.watchlist,
                        state.selectedSymbol,
                        {onIntent(RadarIntent.SelectTicker(it))},
                        Modifier.width(292.dp).fillMaxHeight()
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(RadarColors.Border))
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

@Composable
private fun AppNavigation(page:AppPage,onIntent:(RadarIntent)->Unit){
    val items=listOf(
        AppPage.DASHBOARD to "Dashboard",
        AppPage.RADAR to "Asset",
        AppPage.PORTFOLIO to "Portfolio",
        AppPage.WATCHLIST to "Watchlist",
        AppPage.SCREENER to "Screener",
        AppPage.EVENTS to "Events",
        AppPage.PROVIDERS to "Providers",
        AppPage.SETTINGS to "Settings"
    )
    Column(
        Modifier.width(106.dp).fillMaxHeight().background(RadarColors.Panel).padding(vertical=12.dp),
        horizontalAlignment=Alignment.CenterHorizontally
    ){
        Box(
            Modifier.size(42.dp).background(RadarColors.Accent),
            contentAlignment=Alignment.Center
        ){
            Text("IR",color=RadarColors.Background,fontWeight=FontWeight.Black,fontSize=12.sp)
        }
        Spacer(Modifier.height(18.dp))
        items.forEach{(target,label)->
            val selected=target==page
            Box(
                Modifier.fillMaxWidth().height(48.dp)
                    .background(if(selected)RadarColors.Selected else RadarColors.Panel)
                    .clickable{onIntent(RadarIntent.Navigate(target))}
                    .padding(horizontal=10.dp),
                contentAlignment=Alignment.CenterStart
            ){
                Text(
                    label,
                    color=if(selected)RadarColors.Text else RadarColors.TextMuted,
                    fontSize=9.sp,
                    fontWeight=if(selected)FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text("C++",color=RadarColors.TextMuted,fontSize=8.sp)
    }
}

@Composable
private fun AppTopBar(state:RadarUiState,onIntent:(RadarIntent)->Unit){
    var search by remember(state.selectedSymbol){mutableStateOf(state.selectedSymbol)}
    Row(
        Modifier.fillMaxWidth().height(58.dp).background(RadarColors.Panel).padding(horizontal=14.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Text(
            when(state.page){
                AppPage.DASHBOARD -> "Dashboard"
                AppPage.RADAR -> "Asset analysis"
                AppPage.PORTFOLIO -> "Portfolio"
                AppPage.WATCHLIST -> "Watchlist"
                AppPage.SCREENER -> "Screener"
                AppPage.EVENTS -> "Events"
                AppPage.PROVIDERS -> "Providers & routing"
                AppPage.SETTINGS -> "Settings"
            },
            color=RadarColors.Text,
            fontWeight=FontWeight.Bold,
            fontSize=13.sp
        )
        Spacer(Modifier.weight(1f))
        if(state.page==AppPage.RADAR || state.page==AppPage.DASHBOARD){
            OutlinedTextField(
                value=search,
                onValueChange={search=it.uppercase()},
                modifier=Modifier.width(240.dp),
                singleLine=true,
                textStyle=LocalTextStyle.current.copy(fontSize=10.sp),
                placeholder={Text("Ticker / company",fontSize=9.sp)},
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(onSearch={
                    onIntent(RadarIntent.SelectTicker(search))
                })
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            if(state.status.mode=="demo")"DEMO" else if(state.status.live)"LIVE" else "LIVE · NOT VALIDATED",
            color=when{
                state.status.mode=="demo"->RadarColors.TextMuted
                state.status.live->RadarColors.Positive
                else->RadarColors.Negative
            },
            fontSize=8.sp
        )
        Spacer(Modifier.width(12.dp))
        TextButton(onClick={onIntent(RadarIntent.Refresh)}){Text("Refresh",fontSize=9.sp)}
    }
}
