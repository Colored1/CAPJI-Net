package com.capji.netpulse.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.domain.model.RouterType
import com.capji.netpulse.ui.components.ConnectionPill
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.ui.components.RadarPulse
import com.capji.netpulse.ui.components.Stagger
import com.capji.netpulse.ui.viewmodel.LoginViewModel

@Composable
fun LoginScreen(modifier: Modifier = Modifier, vm: LoginViewModel = viewModel()) {
    val conn by vm.connection.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val connecting = conn is ConnectionState.Connecting
    var showPass by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Stagger(0) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RadarPulse(scanning = connecting, deviceCount = 0, size = 150.dp)
                Text("CAPJI Net", style = MaterialTheme.typography.headlineMedium)
                Text("Control every device on your Wi-Fi", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Stagger(1) { ConnectionPill(conn) }

        if (profiles.isNotEmpty()) {
            Stagger(2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saved routers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profiles.forEach { p ->
                            InputChip(
                                selected = false,
                                onClick = { vm.use(p) },
                                label = { Text(p.name) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close, "Remove",
                                        Modifier.size(16.dp).clickable { vm.delete(p) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        Stagger(3) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Router type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouterType.entries.forEach { t ->
                        FilterChip(selected = vm.type == t, onClick = { vm.type = t }, label = { Text(t.title) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vm.host,
                    onValueChange = { vm.host = it },
                    label = { Text("Gateway / router IP") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        IconButton(onClick = vm::detectGateway) { Icon(Icons.Filled.MyLocation, "Detect gateway") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.user,
                    onValueChange = { vm.user = it },
                    label = { Text("Admin username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.pass,
                    onValueChange = { vm.pass = it },
                    label = { Text("Admin password") },
                    singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vm.remember, onCheckedChange = { vm.remember = it })
                    Text("Save router profile (password is never stored)", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = vm::connect,
                    enabled = !connecting && vm.host.isNotBlank() && vm.pass.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    AnimatedContent(connecting, label = "connectBtn") { busy ->
                        if (busy) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                Spacer(Modifier.width(10.dp))
                                Text("Connecting...")
                            }
                        } else {
                            Text("Connect")
                        }
                    }
                }
                TextButton(onClick = vm::demo, enabled = !connecting, modifier = Modifier.fillMaxWidth()) {
                    Text("Try the demo router (no hardware needed)")
                }
            }
        }

        Stagger(4) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("How NetPulse talks to your router", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your phone only controls what the router itself exposes (REST API / ubus / web session). " +
                        "Android can discover devices on the LAN, but it cannot block or throttle other devices on its own - " +
                        "the router must enforce the rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
