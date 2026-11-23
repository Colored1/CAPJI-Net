package com.capji.netpulse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capji.netpulse.AppGraph
import com.capji.netpulse.R
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.ui.components.CapjiNetworkLogo
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.ui.util.Contact

/** "About CAPJI & contact the developer": attribution, rights, and one-tap links with safe fallbacks. */
@Composable
fun AboutContactCard(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    fun go(uri: String, fallback: String? = null) {
        if (!Contact.open(ctx, uri) && (fallback == null || !Contact.open(ctx, fallback))) AppGraph.toast(UiText(R.string.link_failed))
    }
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapjiNetworkLogo(size = 64.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.about_contact_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_dev), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.about_rights), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { go(Contact.instagram) }, label = { Text("Instagram") }, leadingIcon = { Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { go(Contact.tiktok) }, label = { Text("TikTok") }, leadingIcon = { Icon(Icons.Filled.PlayCircle, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { go(Contact.whatsapp, Contact.tel) }, label = { Text("WhatsApp") }, leadingIcon = { Icon(Icons.Filled.Chat, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { go(Contact.tel) }, label = { Text(stringResource(R.string.contact_call)) }, leadingIcon = { Icon(Icons.Filled.Call, null, Modifier.size(18.dp)) })
        }
    }
}
