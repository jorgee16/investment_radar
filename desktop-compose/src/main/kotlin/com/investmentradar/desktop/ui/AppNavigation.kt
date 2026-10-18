package com.investmentradar.desktop.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.investmentradar.desktop.model.*

@Composable fun AppNavigation(page:AppPage,onIntent:(RadarIntent)->Unit,compact:Boolean){
 val items=listOf(
  Triple(AppPage.DASHBOARD,"Dashboard",Icons.Outlined.Home),
  Triple(AppPage.RADAR,"Asset",Icons.Outlined.ShowChart),
  Triple(AppPage.PORTFOLIO,"Portfolio",Icons.Outlined.AccountBalanceWallet),
  Triple(AppPage.WATCHLIST,"Watchlist",Icons.Outlined.StarBorder),
  Triple(AppPage.SCREENER,"Screener",Icons.Outlined.FilterList),
  Triple(AppPage.EVENTS,"Events",Icons.Outlined.Event),
  Triple(AppPage.PROVIDERS,"Providers",Icons.Outlined.CloudQueue),
  Triple(AppPage.SETTINGS,"Settings",Icons.Outlined.Settings)
 )
 Column(Modifier.width(if(compact)64.dp else 152.dp).fillMaxHeight().background(RadarColors.Panel).verticalScroll(rememberScrollState()).padding(vertical=12.dp)){
  Text("IR",color=RadarColors.Accent,fontSize=18.sp,modifier=Modifier.padding(16.dp))
  items.forEach{(target,label,icon)->
   val active=target==page
   Row(Modifier.fillMaxWidth().height(50.dp).background(if(active)RadarColors.Selected else RadarColors.Panel)
    .clickable{onIntent(RadarIntent.Navigate(target))}.padding(horizontal=if(compact)20.dp else 14.dp),
    verticalAlignment=Alignment.CenterVertically){
    Icon(icon,contentDescription=label,tint=if(active)RadarColors.Accent else RadarColors.TextMuted,modifier=Modifier.size(20.dp))
    if(!compact){Spacer(Modifier.width(12.dp));Text(label,color=if(active)RadarColors.Text else RadarColors.TextMuted,fontSize=11.sp)}
   }
  }
 }
}
