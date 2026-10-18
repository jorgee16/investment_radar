package com.investmentradar.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable internal fun MarketIcon() {
    val ink = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(1.8.dp.toPx())
        drawCircle(ink, size.minDimension * .42f, style = stroke)
        drawOval(ink, Offset(size.width * .28f, size.height * .08f), Size(size.width * .44f, size.height * .84f), style = stroke)
        drawLine(ink, Offset(size.width * .08f, size.height / 2), Offset(size.width * .92f, size.height / 2), 1.8.dp.toPx())
    }
}
@Composable internal fun WalletIcon() {
    val ink = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        drawRoundRect(ink, Offset(size.width * .08f, size.height * .2f), Size(size.width * .84f, size.height * .64f), CornerRadius(2.dp.toPx()), style = Stroke(1.8.dp.toPx()))
        drawLine(ink, Offset(size.width * .1f, size.height * .35f), Offset(size.width * .9f, size.height * .35f), 1.8.dp.toPx())
        drawCircle(ink, 1.5.dp.toPx(), Offset(size.width * .75f, size.height * .58f))
    }
}
