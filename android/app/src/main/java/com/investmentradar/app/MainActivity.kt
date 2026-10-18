package com.investmentradar.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    internal val model: RadarViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(android.graphics.Color.WHITE, android.graphics.Color.BLACK), navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.WHITE, android.graphics.Color.BLACK))
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !state.dark
                    isAppearanceLightNavigationBars = !state.dark
                }
            }
            RadarApp(model, state)
        }
        if (!model.state.value.ready && model.state.value.error == null) LegacyPortfolioMigration(this, model::completeMigration, model::migrationFailed).start()
    }
}
