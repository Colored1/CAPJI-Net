package com.capji.netpulse.ui.screens

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.ui.components.NpBoundsTransform
import com.capji.netpulse.ui.components.Stagger
import com.capji.netpulse.ui.components.StatusBadge
import com.capji.netpulse.ui.components.icon
import com.capji.netpulse.ui.components.quotaColor
import com.capji.netpulse.ui.components.visual
import com.capji.netpulse.ui.theme.NpColors
import com.capji.netpulse.ui.util.formatCountdown
import com.capji.netpulse.ui.util.formatMb
import com.capji.netpulse.ui.util.formatRate
import com.capji.netpulse.ui.viewmodel.DevicesViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DeviceControlScreen(
    device: NetDevice,
    caps: Set<Capability>,
    vm: DevicesViewModel,
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    var showLimit by remember { mutableStateOf(false) }
    var showQuota by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    val accent by animateColorAsState(device.status.visual().color, label = "ctrlAccent")

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.select(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) }
            Text(stringResource(R.string.ctl_title), style = MaterialTheme.typography.titleLarge)
        }

        // ---- Header: shares bounds + avatar with the list card -------------------------------
        with(shared) {
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .sharedBounds(rememberSharedContentState("card-${device.id}"), visibility, boundsTransform = NpBoundsTransform),
                accent = accent,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .sharedElement(rememberSharedContentState("avatar-${device.id}"), visibility, boundsTransform = NpBoundsTransform)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(device.kind.icon(), null, tint = accent, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(device.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(vendorLabel(device.vendor), color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        StatusBadge(device.status)
                    }
                }
                Spacer(Modifier.height(14.dp))
                InfoRow(stringResource(R.string.lbl_hostname), device.hostname.ifBlank { "-" })
                InfoRow(stringResource(R.string.lbl_ip), device.ip)
                InfoRow(stringResource(R.string.lbl_mac), device.mac, mono = true)
                InfoRow(stringResource(R.string.lbl_connection), device.connection.label)
                InfoRow(stringResource(R.string.lbl_live), "↓ ${formatRate(device.liveDownKbps)}   ↑ ${formatRate(device.liveUpKbps)}")
                InfoRow(stringResource(R.string.lbl_session), "↓ ${formatMb(device.sessionDownMb)}   ↑ ${formatMb(device.sessionUpMb)}")
            }
        }

        if (device.randomizedMac) {
            Stagger(0) {
                Note(
                    stringResource(R.string.note_private_mac),
                )
            }
        }

        // ---- Block ---------------------------------------------------------------------------
        Stagger(1) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionIcon(Icons.Filled.Block, NpColors.Blocked)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.block_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (device.blocked) stringResource(R.string.block_on_desc) else stringResource(R.string.block_off_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = device.blocked,
                        enabled = Capability.BLOCK in caps,
                        onCheckedChange = { want ->
                            if (want) {
                                confirmBlock = true
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.setBlocked(device, false)
                            }
                        },
                    )
                }
            }
        }

        // ---- Speed limit -----------------------------------------------------------------------
        Stagger(2) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionIcon(Icons.Filled.Speed, NpColors.Throttled)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.limit_title), style = MaterialTheme.typography.titleMedium)
                        val l = device.limit
                        Text(
                            if (l == null) stringResource(R.string.unlimited)
                            else stringResource(R.string.limit_line_fmt, rateLabel(l.downKbps), rateLabel(l.upKbps)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (l == null) scheme.onSurfaceVariant else NpColors.Throttled,
                        )
                    }
                    FilledTonalButton(onClick = { showLimit = true }, enabled = Capability.SPEED_LIMIT in caps) {
                        Text(if (device.limit == null) stringResource(R.string.set) else stringResource(R.string.edit))
                    }
                }
                if (Capability.SPEED_LIMIT !in caps) UnsupportedHint(stringResource(R.string.limit_unsupported))
            }
        }

        // ---- Quota -----------------------------------------------------------------------------
        Stagger(3) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionIcon(Icons.Filled.DataUsage, NpColors.Quota)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.quota_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            device.quota?.let { stringResource(R.string.quota_cap_line, formatMb(it.capMb), stringResource(it.action.labelRes)) } ?: stringResource(R.string.quota_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { showQuota = true }, enabled = Capability.QUOTA in caps) {
                        Text(if (device.quota == null) stringResource(R.string.set) else stringResource(R.string.edit))
                    }
                }
                device.quota?.let { q ->
                    Spacer(Modifier.height(12.dp))
                    val p by animateFloatAsState(q.fraction, spring(stiffness = Spring.StiffnessLow), label = "ctrlQuota")
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        color = quotaColor(q.fraction, q.reached),
                        trackColor = scheme.onSurface.copy(alpha = 0.08f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (q.reached) stringResource(R.string.quota_used_reached, formatMb(q.usedMb))
                        else stringResource(R.string.quota_used_remaining, formatMb(q.usedMb), formatMb(q.remainingMb)),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (Capability.QUOTA !in caps) {
                    UnsupportedHint(stringResource(R.string.quota_unsupported))
                } else {
                    UnsupportedHint(stringResource(R.string.quota_hint))
                }
            }
        }

        // ---- Pause timer -----------------------------------------------------------------------
        Stagger(4) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionIcon(Icons.Filled.Timer, NpColors.Paused)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pause_title), style = MaterialTheme.typography.titleMedium)
                        val until = device.pausedUntilMs
                        if (until == null) {
                            Text(stringResource(R.string.pause_auto), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        } else {
                            val now by produceState(System.currentTimeMillis(), until) {
                                while (true) {
                                    value = System.currentTimeMillis()
                                    delay(1000)
                                }
                            }
                            Text(
                                stringResource(R.string.pause_resumes_in, formatCountdown((until - now) / 1000)),
                                style = MaterialTheme.typography.bodySmall,
                                color = NpColors.Paused,
                            )
                        }
                    }
                    if (device.pausedUntilMs != null) {
                        TextButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.pause(device, 0)
                        }) { Text(stringResource(R.string.resume_now)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15 to R.string.dur_15m, 30 to R.string.dur_30m, 60 to R.string.dur_1h, 120 to R.string.dur_2h).forEach { (min, labelRes) ->
                        AssistChip(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.pause(device, min)
                            },
                            label = { Text(stringResource(labelRes)) },
                            enabled = Capability.PAUSE in caps,
                        )
                    }
                }
                if (Capability.PAUSE !in caps) UnsupportedHint(stringResource(R.string.pause_unsupported))
            }
        }
        ScheduleCard(device, caps)
        AssistCard(device)
        Spacer(Modifier.height(24.dp))
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text(stringResource(R.string.block_dialog_title, device.displayName)) },
            text = { Text(stringResource(R.string.block_dialog_text)) },
            confirmButton = {
                Button(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.setBlocked(device, true)
                    confirmBlock = false
                }) { Text(stringResource(R.string.block)) }
            },
            dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showLimit) {
        LimitSheet(
            initial = device.limit,
            onApply = { vm.setLimit(device, it) },
            onDismiss = { showLimit = false },
        )
    }
    if (showQuota) {
        QuotaSheet(
            initial = device.quota,
            onApply = { vm.setQuota(device, it) },
            onDismiss = { showQuota = false },
        )
    }
}

@Composable
private fun SectionIcon(icon: ImageVector, color: Color) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color)
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun UnsupportedHint(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Note(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NpColors.Throttled.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, null, tint = NpColors.Throttled, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
