package com.capji.netpulse.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capji.netpulse.domain.model.SpeedPhase
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.ui.components.SpeedGauge
import com.capji.netpulse.ui.components.Stagger
import com.capji.netpulse.ui.theme.NpColors
import com.capji.netpulse.ui.util.fmt1
import com.capji.netpulse.ui.util.fmtSpeed
import com.capji.netpulse.ui.util.formatTime
import com.capji.netpulse.ui.viewmodel.SpeedTestViewModel

private fun niceMax(v: Float): Float =
    listOf(25f, 50f, 100f, 250f, 500f, 1000f).firstOrNull { v <= it * 0.95f } ?: 2000f

@Composable
fun SpeedTestScreen(modifier: Modifier = Modifier, vm: SpeedTestViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val simulated by vm.simulated.collectAsStateWithLifecycle()
    val running = s.phase == SpeedPhase.PING || s.phase == SpeedPhase.DOWNLOAD || s.phase == SpeedPhase.UPLOAD
    val scheme = MaterialTheme.colorScheme

    // The dial scale grows (25 -> 50 -> 100 ...) as the measured speed climbs; the spring in
    // SpeedGauge makes that re-scale feel like a smooth needle swing instead of a jump.
    var peak by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(s.phase) { if (s.phase == SpeedPhase.PING) peak = 0f }
    LaunchedEffect(s.liveMbps) { if (s.liveMbps > peak) peak = s.liveMbps }

    val phaseLabel = when (s.phase) {
        SpeedPhase.IDLE -> "ready"
        SpeedPhase.PING -> "measuring latency"
        SpeedPhase.DOWNLOAD -> "download"
        SpeedPhase.UPLOAD -> "upload"
        SpeedPhase.DONE -> "complete"
        SpeedPhase.ERROR -> "error"
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Speed test", style = MaterialTheme.typography.headlineMedium)
                    Text("Ping - Jitter - Download - Upload", color = scheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Simulate", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    Switch(checked = simulated, onCheckedChange = vm::setSimulated, enabled = !running)
                }
            }
        }
        item {
            SpeedGauge(
                value = s.liveMbps,
                maxValue = niceMax(peak),
                label = phaseLabel,
                modifier = Modifier.fillMaxWidth(0.88f),
            )
        }
        item { PhaseSteps(s.phase) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("Ping", s.pingMs, "ms", Icons.Filled.Timer, s.phase == SpeedPhase.PING, Modifier.weight(1f))
                    MetricTile("Jitter", s.jitterMs, "ms", Icons.Filled.GraphicEq, s.phase == SpeedPhase.PING, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile("Download", s.downMbps, "Mbps", Icons.Filled.ArrowDownward, s.phase == SpeedPhase.DOWNLOAD, Modifier.weight(1f))
                    MetricTile("Upload", s.upMbps, "Mbps", Icons.Filled.ArrowUpward, s.phase == SpeedPhase.UPLOAD, Modifier.weight(1f))
                }
            }
        }
        item { StartButton(running = running, onClick = { if (running) vm.stop() else vm.start() }) }
        s.error?.let { err -> item { Text(err, color = NpColors.Blocked, style = MaterialTheme.typography.bodySmall) } }

        item {
            Text(
                "History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (history.isEmpty()) {
            item {
                Text("No tests yet - tap GO.", color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            }
        }
        items(history, key = { it.timeMs }) { r ->
            GlassCard(Modifier.fillMaxWidth().animateItem()) {
                Text(formatTime(r.timeMs), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("v ${fmtSpeed(r.downMbps)}", color = NpColors.Online, fontWeight = FontWeight.SemiBold)
                    Text("^ ${fmtSpeed(r.upMbps)}", color = NpColors.Paused, fontWeight = FontWeight.SemiBold)
                    Text("${fmt1(r.pingMs)} ms", style = MaterialTheme.typography.bodyMedium)
                    Text("j ${fmt1(r.jitterMs)}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PhaseSteps(phase: SpeedPhase) {
    val scheme = MaterialTheme.colorScheme
    val steps = listOf(SpeedPhase.PING to "Ping", SpeedPhase.DOWNLOAD to "Download", SpeedPhase.UPLOAD to "Upload")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEach { (p, name) ->
            val done = phase == SpeedPhase.DONE || (phase.ordinal in 1..3 && phase.ordinal > p.ordinal)
            val active = phase == p
            val c by animateColorAsState(
                when {
                    active -> scheme.primary
                    done -> NpColors.Online
                    else -> scheme.onSurface.copy(alpha = 0.15f)
                },
                tween(300), label = "step-$name",
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c))
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active || done) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricTile(title: String, value: Float?, unit: String, icon: ImageVector, active: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val accent by animateColorAsState(if (active) scheme.primary else Color.Transparent, tween(250), label = "tile-$title")
    GlassCard(modifier, accent = if (active) accent else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value?.let { fmtSpeed(it) } ?: "-", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}

/** Big round GO button: idle = breathing glow, running = Stop icon. */
@Composable
private fun StartButton(running: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "startPulse")
    val glow by t.animateFloat(0.3f, 0.8f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    Box(
        Modifier
            .size(96.dp)
            .drawBehind {
                if (!running) {
                    drawCircle(scheme.primary.copy(alpha = glow * 0.35f), radius = size.minDimension / 2f * (1f + glow * 0.3f))
                }
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(running, label = "startIcon") { r ->
            if (r) Icon(Icons.Filled.Stop, "Stop", tint = scheme.onPrimary, modifier = Modifier.size(36.dp))
            else Text("GO", color = scheme.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}
