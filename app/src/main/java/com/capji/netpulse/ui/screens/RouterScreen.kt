package com.capji.netpulse.ui.screens

import androidx.compose.ui.res.stringResource
import com.capji.netpulse.R
import com.capji.netpulse.domain.model.asString
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.ui.components.ConnectionPill
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.ui.components.Stagger
import com.capji.netpulse.ui.theme.NpColors

@Composable
fun RouterScreen(conn: ConnectionState.Connected, onDisconnect: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.router_title), style = MaterialTheme.typography.headlineMedium)

        Stagger(0) {
            GlassCard(Modifier.fillMaxWidth()) {
                ConnectionPill(conn)
                Spacer(Modifier.height(12.dp))
                InfoLine(stringResource(R.string.lbl_profile), conn.profile.name)
                InfoLine(stringResource(R.string.lbl_driver), stringResource(conn.profile.type.titleRes))
                InfoLine(stringResource(R.string.lbl_model), conn.info.model)
                InfoLine(stringResource(R.string.lbl_firmware), conn.info.firmware)
                InfoLine(stringResource(R.string.lbl_gateway), conn.info.gateway)
                InfoLine(stringResource(R.string.lbl_user), conn.profile.username)
            }
        }

        Stagger(1) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.router_can_enforce), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Capability.entries.forEach { c ->
                    val ok = c in conn.info.capabilities
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                            null,
                            tint = if (ok) NpColors.Online else scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(c.labelRes), color = if (ok) scheme.onSurface else scheme.onSurfaceVariant)
                    }
                }
            }
        }

        Stagger(2) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.good_to_know), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                conn.info.notes.forEach {
                    Text("•  ${it.asString()}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }

        Stagger(3) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(
                onClick = {
                    runCatching {
                        ctx.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("http://" + conn.info.gateway))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.open_router_page)) }
        }

        Stagger(4) { AssistCard(null) }

        Stagger(5) {
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.disconnect)) }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
