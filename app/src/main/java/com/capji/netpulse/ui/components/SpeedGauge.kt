package com.capji.netpulse.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capji.netpulse.ui.util.fmtSpeed

/**
 * 240-degree arc gauge with gradient progress, tick marks and a physical-feeling needle.
 * Motion: the arc and the needle share ONE low-bounce spring, so the needle "overshoots" slightly
 * the way an analogue meter would, and the number tweens separately for readability.
 */
@Composable
fun SpeedGauge(
    value: Float,
    maxValue: Float,
    label: String,
    modifier: Modifier = Modifier,
    unit: String = "Mbps",
) {
    val progress by animateFloatAsState(
        targetValue = (value / maxValue).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "gaugeProgress",
    )
    val shown by animateFloatAsState(value, tween(250), label = "gaugeNumber")

    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val secondary = scheme.tertiary
    val track = scheme.onSurface.copy(alpha = 0.08f)
    val tick = scheme.onSurface.copy(alpha = 0.28f)

    val sweepTotal = 240f
    val startAngle = 150f

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2f + 6.dp.toPx()
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val radius = arcSize.width / 2f

            // track
            drawArc(track, startAngle, sweepTotal, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))

            // progress: draw in a rotated frame so the sweep gradient starts exactly at the arc start
            rotate(startAngle, center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to primary,
                        (sweepTotal / 360f) to secondary,
                        1f to secondary,
                        center = center,
                    ),
                    startAngle = 0f,
                    sweepAngle = sweepTotal * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            // ticks
            for (i in 0..10) {
                rotate(startAngle + sweepTotal * i / 10f, center) {
                    val outer = radius - stroke / 2f - 10.dp.toPx()
                    drawLine(tick, Offset(center.x + outer - 8.dp.toPx(), center.y), Offset(center.x + outer, center.y), 2.dp.toPx())
                }
            }

            // needle + hub
            rotate(startAngle + sweepTotal * progress, center) {
                drawLine(
                    Color.White,
                    Offset(center.x + 10.dp.toPx(), center.y),
                    Offset(center.x + radius - stroke / 2f - 30.dp.toPx(), center.y),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(Color.White, 9.dp.toPx(), center)
            drawCircle(primary, 4.dp.toPx(), center)
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(fmtSpeed(shown), fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text("$unit  -  $label", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}
