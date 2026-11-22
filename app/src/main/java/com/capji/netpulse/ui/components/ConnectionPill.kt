package com.capji.netpulse.ui.components

import androidx.compose.ui.res.stringResource
import com.capji.netpulse.R
import com.capji.netpulse.domain.model.asString
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.ui.theme.NpColors

@Composable
fun PulseDot(color: Color, pulsing: Boolean) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dotAlpha")
    Row(
        Modifier
            .size(9.dp)
            .alpha(if (pulsing) a else 1f)
            .clip(CircleShape)
            .background(color),
    ) {}
}

/** Clear router connection status: Not connected / Connecting / Connected / Failed. */
@Composable
fun ConnectionPill(state: ConnectionState, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = state,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        modifier = modifier,
        label = "connectionPill",
    ) { s ->
        val (text, color, pulsing) = when (s) {
            is ConnectionState.Disconnected -> Triple(stringResource(R.string.conn_not_connected), MaterialTheme.colorScheme.onSurfaceVariant, false)
            is ConnectionState.Connecting -> Triple(stringResource(R.string.conn_connecting), NpColors.Throttled, true)
            is ConnectionState.Connected -> Triple(stringResource(R.string.conn_connected, s.profile.host), NpColors.Online, false)
            is ConnectionState.Failed -> Triple(s.message.asString(), NpColors.Blocked, false)
        }
        Row(
            Modifier
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f))
                .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulseDot(color, pulsing)
            Spacer(Modifier.width(8.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
