package com.capji.netpulse.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** CAPJI monogram ("C") with a network node and radio waves; the waves softly pulse. Same art as the app icon. */
@Composable
fun CapjiNetworkLogo(modifier: Modifier = Modifier, size: Dp = 96.dp, animate: Boolean = true) {
    val t = rememberInfiniteTransition(label = "logo")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val teal = Color(0xFF58E6D9)
    val blue = Color(0xFF6EA8FF)
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 108f
        val c = Offset(54f * u, 54f * u)
        // glow ring that expands and fades
        if (animate) {
            val r = (24f + 12f * pulse) * u
            drawCircle(teal.copy(alpha = 0.22f * (1f - pulse)), radius = r, center = c, style = Stroke(width = 2f * u))
        }
        drawCircle(teal.copy(alpha = 0.10f), radius = 33f * u, center = c)
        // "C": arc from -40deg sweeping 280deg counter-clockwise (open to the right)
        drawArc(
            color = teal, startAngle = -40f, sweepAngle = -280f, useCenter = false,
            topLeft = Offset((54f - 24f) * u, (54f - 24f) * u), size = Size(48f * u, 48f * u),
            style = Stroke(width = 7f * u, cap = StrokeCap.Round),
        )
        drawCircle(blue, radius = 4.5f * u, center = c)
        val w1 = if (animate) 0.55f + 0.45f * (1f - pulse) else 1f
        val w2 = if (animate) 0.35f + 0.65f * pulse else 1f
        drawArc(blue.copy(alpha = w1), -35f, 70f, false, Offset((54f - 11f) * u, (54f - 11f) * u), Size(22f * u, 22f * u), style = Stroke(3.2f * u, cap = StrokeCap.Round))
        drawArc(Color(0xFF9DC1FF).copy(alpha = w2), -35f, 70f, false, Offset((54f - 17f) * u, (54f - 17f) * u), Size(34f * u, 34f * u), style = Stroke(3.2f * u, cap = StrokeCap.Round))
    }
}
