package com.capji.netpulse.ui.remote

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capji.netpulse.AppGraph
import com.capji.netpulse.R
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.remote.RemoteConfigManager
import com.capji.netpulse.ui.components.CapjiNetworkLogo
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.ui.util.Contact
import kotlinx.coroutines.delay

private fun prefs(c: Context) = c.getSharedPreferences("remote_ui", Context.MODE_PRIVATE)

/** Only http(s) links from remote sources are ever opened. */
fun openRemoteLink(context: Context, url: String): Boolean {
    val u = url.trim()
    if (!(u.startsWith("https://") || u.startsWith("http://") || u.startsWith("market://"))) return false
    return Contact.open(context, u)
}

private fun installedVersion(context: Context): Long = runCatching {
    PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
}.getOrDefault(0L)

/** Keeps remote values fresh while the app is open, and shows the update dialog + maintenance lock. */
@Composable
fun RemoteOverlays() {
    val ctx = LocalContext.current
    val remote by RemoteConfigManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            RemoteConfigManager.fetch()
            delay(15 * 60_000L)
        }
    }

    // ---- update dialog -------------------------------------------------------------------------------
    val installed = remember { installedVersion(ctx) }
    if (remote.latestVersionCode > installed) {
        val force = remote.forceUpdate
        var hidden by remember(remote.latestVersionCode) { mutableStateOf(!force && prefs(ctx).getLong("dismissed_update", 0) == remote.latestVersionCode) }
        if (!hidden || force) {
            val msg = remote.updateMessage.ifBlank { stringResource(if (force) R.string.update_force_default else R.string.update_default) }
            AlertDialog(
                onDismissRequest = { if (!force) { prefs(ctx).edit().putLong("dismissed_update", remote.latestVersionCode).apply(); hidden = true } },
                properties = DialogProperties(dismissOnBackPress = !force, dismissOnClickOutside = !force),
                title = { Text(stringResource(R.string.update_title)) },
                text = { Text(msg) },
                confirmButton = {
                    Button(onClick = {
                        val url = remote.updateUrl.ifBlank { "market://details?id=${ctx.packageName}" }
                        if (!openRemoteLink(ctx, url) && !openRemoteLink(ctx, "https://play.google.com/store/apps/details?id=${ctx.packageName}")) {
                            AppGraph.toast(UiText(R.string.link_failed))
                        }
                    }) { Text(stringResource(R.string.update_now)) }
                },
                dismissButton = if (force) null else ({
                    TextButton(onClick = { prefs(ctx).edit().putLong("dismissed_update", remote.latestVersionCode).apply(); hidden = true }) {
                        Text(stringResource(R.string.update_later))
                    }
                }),
            )
        }
    }

    // ---- maintenance lock ----------------------------------------------------------------------------
    if (remote.maintenance) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CapjiNetworkLogo(size = 110.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.maint_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        remote.maintenanceMessage.ifBlank { stringResource(R.string.maint_default) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { RemoteConfigManager.fetch() }) { Text(stringResource(R.string.maint_retry)) }
                }
            }
        }
    }
}

/** Dismissible announcement card for the Home screen. Hidden again only for the exact text the user dismissed. */
@Composable
fun AnnouncementCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val remote by RemoteConfigManager.state.collectAsStateWithLifecycle()
    val key = remote.bannerText.hashCode().toString()
    var dismissed by remember(key) { mutableStateOf(prefs(ctx).getString("dismissed_banner", "") == key) }
    if (!remote.bannerEnabled || remote.bannerText.isBlank() || dismissed) return
    GlassCard(modifier.fillMaxWidth(), accent = MaterialTheme.colorScheme.primary) {
        Row(verticalAlignment = Alignment.Top) {
            Text(remote.bannerText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 8.dp))
            IconButton(onClick = { prefs(ctx).edit().putString("dismissed_banner", key).apply(); dismissed = true }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_dismiss))
            }
        }
        if (remote.bannerLink.startsWith("http")) {
            TextButton(onClick = { if (!openRemoteLink(ctx, remote.bannerLink)) AppGraph.toast(UiText(R.string.link_failed)) }) {
                Text(stringResource(R.string.announce_open))
            }
        }
    }
}
