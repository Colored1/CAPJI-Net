package com.capji.netpulse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capji.netpulse.AppGraph
import com.capji.netpulse.R
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.work.Bg
import java.util.Locale

private fun hhmm(min: Int) = String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

/** Daily "blocked from HH:MM to HH:MM" rule. Runs in the background (WorkManager) even when the app is closed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCard(device: NetDevice, caps: Set<Capability>) {
    val profile = (AppGraph.repo.connection.value as? ConnectionState.Connected)?.profile ?: return
    var version by remember { mutableIntStateOf(0) }
    val current = remember(version, device.mac) { Bg.scheduleFor(profile.id, device.mac) }
    var show by remember { mutableStateOf(false) }
    val enabled = Capability.BLOCK in caps

    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sched_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (current == null) stringResource(R.string.sched_none)
            else stringResource(R.string.sched_line, hhmm(current.startMin), hhmm(current.endMin)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { show = true }, enabled = enabled) {
                Text(stringResource(if (current == null) R.string.set else R.string.edit))
            }
            if (current != null) {
                TextButton(onClick = { Bg.removeSchedule(profile.id, device.mac); version++ }) { Text(stringResource(R.string.remove_limit)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.sched_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (show) {
        val start = rememberTimePickerState(initialHour = (current?.startMin ?: 23 * 60) / 60, initialMinute = (current?.startMin ?: 0) % 60, is24Hour = true)
        val end = rememberTimePickerState(initialHour = (current?.endMin ?: 6 * 60) / 60, initialMinute = (current?.endMin ?: 0) % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(stringResource(R.string.sched_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sched_from))
                    TimeInput(state = start)
                    Text(stringResource(R.string.sched_to))
                    TimeInput(state = end)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val s = start.hour * 60 + start.minute
                    val e = end.hour * 60 + end.minute
                    if (s != e) Bg.setSchedule(profile, device.mac, device.displayName, s, e)
                    version++
                    show = false
                }) { Text(stringResource(R.string.apply_limit)) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Opens the router's own web UI inside the app with auto-login / MAC helpers (works with any router brand). */
@Composable
fun AssistCard(device: NetDevice?) {
    val conn = AppGraph.repo.connection.value as? ConnectionState.Connected ?: return
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.assist_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.assist_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = {
            AppGraph.assist.value = AssistRequest(conn.info.gateway, conn.profile.username, AppGraph.sessionPass, device?.mac, device?.displayName)
        }) { Text(stringResource(R.string.assist_open)) }
    }
}
