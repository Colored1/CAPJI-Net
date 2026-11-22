package com.capji.netpulse.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Scanning radar: 3 expanding ripple rings + a rotating sweep. Device "blips" light up when the
 * sweep passes over them and fade afterwards. When [scanning] is false it idles at low intensity.
 */
@Composable
fun RadarPulse(scanning: Boolean, deviceCount: Int, modifier: Modifier = Modifier, size: Dp = 250.dp) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "sweep")
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing)), label = "wave")
    val centerBeat by transition.animateFloat(
        0.85f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "beat",
    )
    val intensity by animateFloatAsState(if (scanning) 1f else 0.35f, tween(600), label = "intensity")
    val primary = MaterialTheme.colorScheme.primary

    Canvas(modifier.size(size)) {
        val c = center
        val r = min(this.size.width, this.size.height) / 2f

        // static range rings
        for (i in 1..3) drawCircle(primary.copy(alpha = 0.12f), radius = r * i / 3f, style = Stroke(1.dp.toPx()))

        // expanding ripples
        for (k in 0 until 3) {
            val p = (wave + k / 3f) % 1f
            drawCircle(primary.copy(alpha = (1f - p) * 0.38f * intensity), radius = r * p, style = Stroke(2.dp.toPx()))
        }

        // sweep (tail trails behind the bright leading edge)
        rotate(sweep, pivot = c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.72f to Color.Transparent,
                    1f to primary.copy(alpha = 0.5f * intensity),
                    center = c,
                ),
                radius = r,
            )
        }

        // device blips
        for (i in 0 until min(deviceCount, 10)) {
            val angle = (i * 137.5f) % 360f
            val dist = r * (0.28f + 0.62f * (((i * 37) % 100) / 100f))
            val rad = angle * PI.toFloat() / 180f
            val pos = Offset(c.x + cos(rad) * dist, c.y + sin(rad) * dist)
            val behind = ((sweep - angle) % 360f + 360f) % 360f // degrees since the sweep passed
            val glow = (1f - behind / 360f).coerceIn(0.15f, 1f)
            drawCircle(primary.copy(alpha = 0.25f * glow), radius = 11.dp.toPx() * glow, center = pos)
            drawCircle(primary.copy(alpha = 0.5f + 0.5f * glow), radius = 3.5.dp.toPx(), center = pos)
        }

        // router at the centre
        drawCircle(primary.copy(alpha = 0.25f), radius = 14.dp.toPx() * centerBeat, center = c)
        drawCircle(primary, radius = 6.dp.toPx(), center = c)
    }
}
