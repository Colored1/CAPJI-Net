package com.capji.netpulse.ui.screens

import androidx.compose.ui.res.stringResource
import com.capji.netpulse.R
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capji.netpulse.domain.model.DeviceStatus
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.ui.components.RadarPulse
import com.capji.netpulse.ui.components.Stagger
import com.capji.netpulse.ui.components.icon
import com.capji.netpulse.ui.components.visual
import com.capji.netpulse.ui.theme.NpColors
import com.capji.netpulse.ui.util.formatRate
import com.capji.netpulse.ui.viewmodel.DevicesViewModel

@Composable
fun HomeScreen(
    vm: DevicesViewModel,
    routerName: String,
    onOpenDevice: (String) -> Unit,
    onSpeedTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val devices = ui.devices
    val online = devices.count { it.status != DeviceStatus.BLOCKED && it.status != DeviceStatus.PAUSED }
    val limited = devices.count { it.status == DeviceStatus.THROTTLED || it.status == DeviceStatus.QUOTA_CAPPED }
    val blocked = devices.count { it.blocked }
    val totalDown = devices.sumOf { it.liveDownKbps }
    val totalUp = devices.sumOf { it.liveUpKbps }
    val top = devices.sortedByDescending { it.liveDownKbps }.take(3)
    val scheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Stagger(0) {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(routerName, color = scheme.onSurfaceVariant)
                }
            }
        }
        item {
            Stagger(1) {
                GlassCard(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    RadarPulse(scanning = ui.scanning, deviceCount = devices.size)
                    Spacer(Modifier.height(8.dp))
                    AnimatedContent(
                        targetState = if (ui.scanning) stringResource(R.string.home_scanning_network) else stringResource(R.string.home_devices_online, online),
                        transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                        label = "radarText",
                    ) { Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = vm::rescan, enabled = !ui.scanning) { Text(stringResource(R.string.scan_network)) }
                }
            }
        }
        item {
            Stagger(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.status_online), online, NpColors.Online, Icons.Filled.Wifi, Modifier.weight(1f))
                    StatTile(stringResource(R.string.status_limited), limited, NpColors.Throttled, Icons.Filled.Speed, Modifier.weight(1f))
                    StatTile(stringResource(R.string.status_blocked), blocked, NpColors.Blocked, Icons.Filled.Block, Modifier.weight(1f))
                }
            }
        }
        item {
            Stagger(3) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.live_traffic), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("↓ ${formatRate(totalDown)}", color = NpColors.Online, style = MaterialTheme.typography.titleLarge)
                        Text("↑ ${formatRate(totalUp)}", color = NpColors.Paused, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(8.dp))
                    Sparkline(ui.throughput, Modifier.fillMaxWidth().height(64.dp), scheme.primary)
                }
            }
        }
        item {
            Stagger(4) { Text(stringResource(R.string.top_consumers), style = MaterialTheme.typography.titleMedium) }
        }
        items(top, key = { it.id }) { d ->
            val maxKbps = (top.maxOfOrNull { it.liveDownKbps } ?: 1).coerceAtLeast(1)
            val p by animateFloatAsState(d.liveDownKbps / maxKbps.toFloat(), spring(stiffness = Spring.StiffnessLow), label = "consumer")
            val color = d.status.visual().color
            GlassCard(Modifier.fillMaxWidth().animateItem(), onClick = { onOpenDevice(d.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(d.kind.icon(), null, tint = color)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                            color = color,
                            trackColor = scheme.onSurface.copy(alpha = 0.08f),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(formatRate(d.liveDownKbps), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            OutlinedButton(onClick = onSpeedTest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.run_speed_test)) }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, color: Color, icon: ImageVector, modifier: Modifier) {
    GlassCard(modifier) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
            label = "stat-$label",
        ) { Text(it.toString(), style = MaterialTheme.typography.headlineMedium) }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier, color: Color) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val dx = size.width / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * dx
            val y = size.height - (v / max) * size.height * 0.9f - 2f
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
