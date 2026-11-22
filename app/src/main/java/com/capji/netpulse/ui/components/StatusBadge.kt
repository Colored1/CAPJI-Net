package com.capji.netpulse.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capji.netpulse.domain.model.DeviceKind
import com.capji.netpulse.domain.model.DeviceStatus
import com.capji.netpulse.ui.theme.NpColors

data class StatusVisual(val label: String, val color: Color, val icon: ImageVector)

fun DeviceStatus.visual(): StatusVisual = when (this) {
    DeviceStatus.ONLINE -> StatusVisual("Online", NpColors.Online, Icons.Filled.Wifi)
    DeviceStatus.BLOCKED -> StatusVisual("Blocked", NpColors.Blocked, Icons.Filled.Block)
    DeviceStatus.THROTTLED -> StatusVisual("Limited", NpColors.Throttled, Icons.Filled.Speed)
    DeviceStatus.QUOTA_CAPPED -> StatusVisual("Quota reached", NpColors.Quota, Icons.Filled.DataUsage)
    DeviceStatus.PAUSED -> StatusVisual("Paused", NpColors.Paused, Icons.Filled.PauseCircle)
}

fun DeviceKind.icon(): ImageVector = when (this) {
    DeviceKind.PHONE -> Icons.Filled.Smartphone
    DeviceKind.TABLET -> Icons.Filled.Tablet
    DeviceKind.LAPTOP -> Icons.Filled.Laptop
    DeviceKind.TV -> Icons.Filled.Tv
    DeviceKind.CONSOLE -> Icons.Filled.SportsEsports
    DeviceKind.IOT -> Icons.Filled.Memory
    DeviceKind.UNKNOWN -> Icons.Filled.DeviceUnknown
}

/** Colour cross-fades with a spring; icon+label swap with a bouncy scale-in whenever the status changes. */
@Composable
fun StatusBadge(status: DeviceStatus, modifier: Modifier = Modifier) {
    val target = status.visual().color
    val color by animateColorAsState(target, spring(stiffness = Spring.StiffnessMedium), label = "badgeColor")
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                (fadeIn(spring()) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.7f)) togetherWith
                    fadeOut(tween(100))
            },
            label = "badgeContent",
        ) { s ->
            val v = s.visual()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(v.icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(v.label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
