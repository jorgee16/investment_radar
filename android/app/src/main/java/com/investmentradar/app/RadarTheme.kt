package com.investmentradar.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val FinanceBlue = Color(0xFF1266C5)
internal val FinanceGreen = Color(0xFF008568)
internal val FinanceRed = Color(0xFFD91638)
private val LightFinance = lightColorScheme(primary = FinanceBlue, secondary = FinanceGreen,
    background = Color(0xFFF4F6FA), surface = Color.White, surfaceVariant = Color(0xFFF0F2F5),
    onSurface = Color(0xFF20252C), onSurfaceVariant = Color(0xFF646C75), outlineVariant = Color(0xFFDDE1E6), error = FinanceRed)
private val DarkFinance = darkColorScheme(primary = Color(0xFF8DBBFF), secondary = Color(0xFF65D8B8),
    background = Color(0xFF10141B), surface = Color(0xFF191F29), surfaceVariant = Color(0xFF252C38),
    onSurface = Color(0xFFE8ECF3), onSurfaceVariant = Color(0xFFADB6C6), outlineVariant = Color(0xFF394353))
@Composable internal fun FinanceTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkFinance else LightFinance, content = content)
}
