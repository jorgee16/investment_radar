package com.investmentradar.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RadarColors {
    val Background=Color(0xFF0B0E11)
    val Panel=Color(0xFF11161D)
    val PanelRaised=Color(0xFF171D25)
    val Border=Color(0xFF252D38)
    val Selected=Color(0xFF182331)
    val Text=Color(0xFFE6EDF3)
    val TextMuted=Color(0xFF8491A3)
    val Positive=Color(0xFF4FD1A5)
    val Negative=Color(0xFFF26D7D)
    val Accent=Color(0xFF5EA3FF)
    val Grid=Color(0xFF1A222C)
    val Crosshair=Color(0xFF677487)
}

@Composable
fun RadarTheme(content:@Composable()->Unit){
    MaterialTheme(
        colorScheme=darkColorScheme(
            background=RadarColors.Background,
            surface=RadarColors.Panel,
            surfaceVariant=RadarColors.PanelRaised,
            primary=RadarColors.Accent,
            onBackground=RadarColors.Text,
            onSurface=RadarColors.Text
        ),
        content=content
    )
}
