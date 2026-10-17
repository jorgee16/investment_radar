package com.investmentradar.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.investmentradar.desktop.data.LoopbackNativeBridge
import com.investmentradar.desktop.data.NativeMarketRepository
import com.investmentradar.desktop.ui.RadarScreen
import com.investmentradar.desktop.ui.RadarTheme
import com.investmentradar.desktop.viewmodel.RadarViewModel

fun main()=application{
    val bridge=remember{LoopbackNativeBridge()}
    val repository=remember{NativeMarketRepository(bridge)}
    val viewModel=remember{RadarViewModel(repository)}
    val state by viewModel.state.collectAsState()

    Window(
        onCloseRequest={
            viewModel.close()
            bridge.close()
            exitApplication()
        },
        title="Investment Radar",
        state=rememberWindowState(width=1440.dp,height=900.dp)
    ){
        RadarTheme{RadarScreen(state,viewModel::dispatch)}
    }
}
