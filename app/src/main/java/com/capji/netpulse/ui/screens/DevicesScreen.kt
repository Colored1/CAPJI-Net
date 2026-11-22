package com.capji.netpulse.ui.screens

import androidx.compose.ui.res.stringResource
import com.capji.netpulse.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capji.netpulse.ui.components.DeviceCard
import com.capji.netpulse.ui.viewmodel.DevicesUi
import com.capji.netpulse.ui.viewmodel.DevicesViewModel

/**
 * SharedTransitionLayout + AnimatedContent: tapping a card morphs it into the control screen,
 * back gesture / back button reverses it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DevicesScreen(vm: DevicesViewModel, modifier: Modifier = Modifier) {
    val ui by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = ui.selectedId != null) { vm.select(null) }

    SharedTransitionLayout(modifier.fillMaxSize()) {
        val sharedScope = this
        AnimatedContent(
            targetState = ui.selectedId,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            label = "devicesNav",
        ) { selectedId ->
            val device = ui.devices.firstOrNull { it.id == selectedId }
            if (selectedId == null || device == null) {
                DeviceList(ui, vm, sharedScope, this)
            } else {
                DeviceControlScreen(device, ui.capabilities, vm, sharedScope, this)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DeviceList(
    ui: DevicesUi,
    vm: DevicesViewModel,
    shared: SharedTransitionScope,
    visibility: AnimatedVisibilityScope,
) {
    val spin = rememberInfiniteTransition(label = "refresh")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle")

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.devices_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (ui.scanning) stringResource(R.string.devices_scanning) else stringResource(R.string.devices_connected_fmt, ui.devices.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = vm::rescan, enabled = !ui.scanning) {
                Icon(Icons.Filled.Refresh, stringResource(R.string.cd_scan), Modifier.rotate(if (ui.scanning) angle else 0f))
            }
        }

        if (ui.devices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.devices_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.devices, key = { it.id }) { d ->
                    DeviceCard(
                        device = d,
                        shared = shared,
                        visibility = visibility,
                        onClick = { vm.select(d.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
