package com.investmentradar.desktop

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.awt.*
import java.awt.image.BufferedImage
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.nio.file.Paths
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.investmentradar.desktop.data.LoopbackNativeBridge
import com.investmentradar.desktop.data.LocalStateStore
import com.investmentradar.desktop.data.NativeMarketRepository
import com.investmentradar.desktop.ui.RadarScreen
import com.investmentradar.desktop.ui.RadarTheme
import com.investmentradar.desktop.viewmodel.RadarViewModel

fun main(){
    val lockPath=Paths.get(System.getProperty("user.home"),".investment-radar-monitor.lock")
    val channel=FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE)
    val lock=channel.tryLock() ?: return
    try { application{
    val bridge=remember{LoopbackNativeBridge()}
    val repository=remember{NativeMarketRepository(bridge)}
    val localStore=remember{LocalStateStore()}
    val viewModel=remember{RadarViewModel(repository,localStore)}
    val state by viewModel.state.collectAsState()

    var visible by remember{mutableStateOf(true)}
    var showMonitor by remember{mutableStateOf(false)}
    val tray=remember { if(SystemTray.isSupported()) TrayIcon(BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB).apply{createGraphics().apply{color=Color(40,180,140);fillOval(2,2,28,28);dispose()}},"Investment Radar").apply{isImageAutoSize=true} else null }
    fun quit(){viewModel.close();bridge.close();exitApplication()}
    DisposableEffect(tray){
        if(tray!=null){val menu=PopupMenu();menu.add(MenuItem("Abrir Investment Radar").apply{addActionListener{visible=true}});menu.add(MenuItem("Sair completamente").apply{addActionListener{quit()}});tray.popupMenu=menu;tray.addActionListener{visible=true;showMonitor=true};SystemTray.getSystemTray().add(tray)}
        onDispose{if(tray!=null)SystemTray.getSystemTray().remove(tray)}
    }
    LaunchedEffect(Unit){while(isActive){
        try {
            val deliveries=Json.parseToJsonElement(bridge.post("/api/monitor/claim","{}")).jsonArray
            for(item in deliveries){val e=item.jsonObject
                val accepted=withContext(Dispatchers.IO){runCatching{
                    if(e.text("channel")=="email") MonitorEmail.send(e)
                    else {require(tray!=null);EventQueue.invokeAndWait{tray.displayMessage("Investment Radar", "${e.text("instrument")}: ${units(e["priceUnits"])} ${e.text("currency")} · ${e.text("basis")} · ${e.text("venue")}",TrayIcon.MessageType.INFO)}}
                }.isSuccess}
                bridge.post("/api/monitor/complete",buildJsonObject{put("id",e.text("deliveryId"));put("leaseToken",e.text("leaseToken"));put("accepted",accepted)}.toString())
            }
        }catch(_:Exception){ /* Outbox leases expire; no credentials or payloads logged. */ }
        delay(2000)
    }}
    Window(
        onCloseRequest={if(tray!=null) visible=false else quit()},
        visible=visible,
        title="Investment Radar",
        state=rememberWindowState(width=1440.dp,height=900.dp)
    ){
        RadarTheme{Column{Row{TextButton(onClick={showMonitor=false}){Text("Aplicação")};TextButton(onClick={showMonitor=true}){Text("Alertas de preço")};TextButton(onClick={quit()}){Text("Sair completamente")}};Box(Modifier.weight(1f)){if(showMonitor)PriceMonitorPanel(bridge) else RadarScreen(state,viewModel::dispatch)}}}
    }
}

    } finally { lock.release();channel.close() }
}
