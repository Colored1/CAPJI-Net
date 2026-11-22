package com.capji.netpulse.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capji.netpulse.AppGraph
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.ui.screens.DevicesScreen
import com.capji.netpulse.ui.screens.HomeScreen
import com.capji.netpulse.ui.screens.LoginScreen
import com.capji.netpulse.ui.screens.RouterScreen
import com.capji.netpulse.ui.screens.SpeedTestScreen
import com.capji.netpulse.ui.theme.NetPulseTheme
import com.capji.netpulse.ui.theme.NpBackground
import com.capji.netpulse.ui.viewmodel.DevicesViewModel
import kotlinx.coroutines.flow.collectLatest

enum class NavTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Dashboard),
    DEVICES("Devices", Icons.Filled.Devices),
    SPEED("Speed", Icons.Filled.Speed),
    ROUTER("Router", Icons.Filled.Router),
}

@Composable
fun NetPulseRoot() {
    NetPulseTheme {
        NpBackground {
            val haptic = LocalHapticFeedback.current
            val conn by AppGraph.repo.connection.collectAsStateWithLifecycle()
            val connected = conn as? ConnectionState.Connected
            // keep the last Connected value so the main UI can finish its exit animation after disconnect
            val last = remember { mutableStateOf<ConnectionState.Connected?>(null) }
            if (connected != null) last.value = connected

            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(Unit) { AppGraph.toasts.collectLatest { snackbar.showSnackbar(it) } }

            var tab by rememberSaveable { mutableStateOf(NavTab.HOME) }
            val devicesVm: DevicesViewModel = viewModel()

            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    AnimatedVisibility(
                        visible = connected != null,
                        enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)) {
                            NavTab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = {
                                        if (tab != t) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        tab = t
                                    },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                AnimatedContent(
                    targetState = connected != null,
                    transitionSpec = { (fadeIn(tween(350)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(200)) },
                    modifier = Modifier.padding(padding),
                    label = "root",
                ) { isConnected ->
                    val info = last.value
                    if (!isConnected || info == null) {
                        LoginScreen()
                    } else {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                            label = "tabs",
                        ) { t ->
                            when (t) {
                                NavTab.HOME -> HomeScreen(
                                    vm = devicesVm,
                                    routerName = info.info.model,
                                    onOpenDevice = { id ->
                                        devicesVm.select(id)
                                        tab = NavTab.DEVICES
                                    },
                                    onSpeedTest = { tab = NavTab.SPEED },
                                )
                                NavTab.DEVICES -> DevicesScreen(devicesVm)
                                NavTab.SPEED -> SpeedTestScreen()
                                NavTab.ROUTER -> RouterScreen(
                                    conn = info,
                                    onDisconnect = {
                                        devicesVm.select(null)
                                        AppGraph.repo.disconnect()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
