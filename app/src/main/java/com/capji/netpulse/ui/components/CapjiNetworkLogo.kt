package com.capji.netpulse.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.capji.netpulse.R

/**
 * The real CAPJI "CJ" monogram with Wi-Fi waves built into the letters, on a light plate so the black "J" stays visible on the
 * dark UI. A soft gold ring pulses from the badge (the "network pulse").
 */
@Composable
fun CapjiNetworkLogo(modifier: Modifier = Modifier, size: Dp = 96.dp, animate: Boolean = true) {
    val t = rememberInfiniteTransition(label = "logo")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.22f))) {
        Image(painterResource(R.drawable.capji_mark), contentDescription = "CAPJI", modifier = Modifier.size(size))
        if (animate) {
            Canvas(Modifier.size(size)) {
                val c = Offset(this.size.width * 0.514f, this.size.height * 0.574f)
                val r = this.size.minDimension * (0.03f + 0.17f * pulse)
                drawCircle(Color(0xFFC4A070).copy(alpha = 0.45f * (1f - pulse)), radius = r, center = c, style = Stroke(width = this.size.minDimension * 0.015f))
            }
        }
    }
}
