package com.capji.netpulse.ui.screens

import android.content.Intent
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capji.netpulse.R
import com.capji.netpulse.core.LanScanner
import com.capji.netpulse.domain.identify.DeviceIdentifier
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.ui.util.vendorLabel
import kotlinx.coroutines.delay
import java.util.Locale

private data class Client(val ip: String, val mac: String?, val name: String?, val vendor: String)

/** Personal (phone) hotspot: honest, read-only monitoring + shortcuts to the system screens that CAN control it. */
@Composable
fun HotspotScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var apState by remember { mutableStateOf<Boolean?>(null) }
    var clients by remember { mutableStateOf<List<Client>?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var subnetFound by remember { mutableStateOf(true) }
    var down by remember { mutableStateOf(0f) }
    var up by remember { mutableStateOf(0f) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        var rx = TrafficStats.getTotalRxBytes()
        var tx = TrafficStats.getTotalTxBytes()
        while (true) {
            delay(1000)
            val nrx = TrafficStats.getTotalRxBytes()
            val ntx = TrafficStats.getTotalTxBytes()
            if (rx >= 0 && nrx >= 0) down = (nrx - rx) * 8 / 1_000_000f
            if (tx >= 0 && ntx >= 0) up = (ntx - tx) * 8 / 1_000_000f
            rx = nrx; tx = ntx
            apState = runCatching {
                val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
                WifiManager::class.java.getDeclaredMethod("isWifiApEnabled").invoke(wm) as Boolean
            }.getOrNull()
        }
    }

    LaunchedEffect(tick) {
        scanning = true
        val sub = LanScanner.hotspotSubnet()
        subnetFound = sub != null
        clients = if (sub == null) emptyList() else {
            val arp = LanScanner.readArp()
            LanScanner.scan(sub).map { f ->
                val mac = f.mac ?: arp[f.ip]
                val v = mac?.let { DeviceIdentifier.identify(it, f.host.orEmpty()).vendor } ?: DeviceIdentifier.VENDOR_UNKNOWN
                Client(f.ip, mac, f.host, v)
            }
        }
        scanning = false
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hotspot_title), style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.cd_back)) }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    when (apState) { true -> R.string.hotspot_on; false -> R.string.hotspot_off; null -> R.string.hotspot_unknown },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.hotspot_speed, String.format(Locale.US, "%.1f", down), String.format(Locale.US, "%.1f", up)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { open(ctx, Settings.ACTION_WIRELESS_SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hotspot_open_settings))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { open(ctx, Settings.ACTION_DATA_USAGE_SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hotspot_open_data))
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hotspot_clients), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { tick++ }, enabled = !scanning) {
                    Text(stringResource(if (scanning) R.string.devices_scanning else R.string.cd_scan))
                }
            }
            Spacer(Modifier.height(8.dp))
            val list = clients.orEmpty()
            if (list.isEmpty() && !scanning) {
                Text(
                    stringResource(if (subnetFound) R.string.hotspot_none else R.string.hotspot_no_interface),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            list.forEach { c ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(c.name ?: c.ip, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(c.ip, c.mac, vendorLabel(c.vendor).takeIf { c.mac != null }).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.hotspot_honest_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.hotspot_honest_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun open(ctx: android.content.Context, action: String) {
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
