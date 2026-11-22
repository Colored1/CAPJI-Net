package com.capji.netpulse.ui.components

import androidx.compose.ui.res.stringResource
import com.capji.netpulse.R
import com.capji.netpulse.ui.util.rateLabel
import com.capji.netpulse.ui.util.vendorLabel
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.ui.theme.NpColors
import com.capji.netpulse.ui.util.formatMb
import com.capji.netpulse.ui.util.formatRate

fun ConnectionType.icon(): ImageVector = when (this) {
    ConnectionType.WIFI_24 -> Icons.Filled.NetworkWifi
    ConnectionType.WIFI_5 -> Icons.Filled.SignalWifi4Bar
    ConnectionType.LAN -> Icons.Filled.Cable
}

/** Colour for a quota bar: primary -> amber (80%+) -> red (reached). */
@Composable
fun quotaColor(fraction: Float, reached: Boolean): Color {
    val target = when {
        reached -> NpColors.Blocked
        fraction >= 0.8f -> NpColors.Throttled
        else -> MaterialTheme.colorScheme.primary
    }
    val c by animateColorAsState(target, label = "quotaColor")
    return c
}

@Composable
fun MiniTag(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
    }
}

/**
 * List card. Its bounds and avatar are SHARED elements: when the control screen opens, the card
 * physically morphs into the screen header (same keys "card-<mac>" / "avatar-<mac>").
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DeviceCard(
    device: NetDevice,
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = device.status
    val accent by animateColorAsState(status.visual().color, label = "accent")
    val scheme = MaterialTheme.colorScheme

    with(shared) {
        GlassCard(
            modifier = modifier.sharedBounds(
                rememberSharedContentState("card-${device.id}"),
                visibility,
                boundsTransform = NpBoundsTransform,
            ),
            onClick = onClick,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .sharedElement(rememberSharedContentState("avatar-${device.id}"), visibility, boundsTransform = NpBoundsTransform)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(device.kind.icon(), null, tint = accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(vendorLabel(device.vendor), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    Text(
                        "${device.ip}  ${device.mac}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(status)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniTag(device.connection.label, scheme.secondary, device.connection.icon())
                Text("↓ ${formatRate(device.liveDownKbps)}", style = MaterialTheme.typography.labelMedium, color = NpColors.Online)
                Text("↑ ${formatRate(device.liveUpKbps)}", style = MaterialTheme.typography.labelMedium, color = NpColors.Paused)
                if (device.randomizedMac) MiniTag(stringResource(R.string.tag_private_mac), NpColors.Throttled)
            }

            device.limit?.let { l ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.limit_card_fmt, rateLabel(l.downKbps), rateLabel(l.upKbps)),
                    style = MaterialTheme.typography.labelSmall,
                    color = NpColors.Throttled,
                )
            }

            device.quota?.let { q ->
                Spacer(Modifier.height(10.dp))
                val p by animateFloatAsState(q.fraction, spring(stiffness = Spring.StiffnessLow), label = "quotaProgress")
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = quotaColor(q.fraction, q.reached),
                    trackColor = scheme.onSurface.copy(alpha = 0.08f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (q.reached) stringResource(R.string.quota_reached_action, stringResource(q.action.labelRes)) else stringResource(R.string.quota_left_of, formatMb(q.remainingMb), formatMb(q.capMb)),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
