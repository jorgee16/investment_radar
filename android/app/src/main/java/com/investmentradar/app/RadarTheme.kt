@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
package com.investmentradar.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val FinanceBlue = Color(0xFF1266C5)
internal val FinanceGreen = Color(0xFF008568)
internal val FinanceRed = Color(0xFFD91638)
private val LightFinance = lightColorScheme(primary = FinanceBlue, secondary = FinanceGreen,
    primaryContainer = Color(0xFFE8F0FF), onPrimaryContainer = FinanceBlue,
    secondaryContainer = Color(0xFFE4F3ED), onSecondaryContainer = Color(0xFF0B5143),
    background = Color(0xFFF4F6FA), surface = Color.White, surfaceVariant = Color(0xFFF0F2F5),
    onSurface = Color(0xFF20252C), onSurfaceVariant = Color(0xFF646C75), outlineVariant = Color(0xFFDDE1E6), error = FinanceRed)
private val DarkFinance = darkColorScheme(primary = Color(0xFF8DBBFF), secondary = Color(0xFF65D8B8),
    primaryContainer = Color(0xFF1B3556), onPrimaryContainer = Color(0xFFC4DEFF),
    secondaryContainer = Color(0xFF183E35), onSecondaryContainer = Color(0xFFAAEBD6),
    background = Color(0xFF10141B), surface = Color(0xFF191F29), surfaceVariant = Color(0xFF252C38),
    onSurface = Color(0xFFE8ECF3), onSurfaceVariant = Color(0xFFADB6C6), outlineVariant = Color(0xFF394353))
internal val RadarFont = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)
private val RadarTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = RadarFont, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = RadarFont, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = RadarFont, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontFamily = RadarFont, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = RadarFont, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = RadarFont, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp),
        titleLarge = base.titleLarge.copy(fontFamily = RadarFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontFamily = RadarFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall = base.titleSmall.copy(fontFamily = RadarFont, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = RadarFont, fontSize = 15.sp, lineHeight = 23.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = RadarFont, fontSize = 13.sp, lineHeight = 20.sp),
        bodySmall = base.bodySmall.copy(fontFamily = RadarFont, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontFamily = RadarFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = RadarFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = RadarFont, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    )
}
@Composable internal fun FinanceTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkFinance else LightFinance, typography = RadarTypography, content = content)
}
