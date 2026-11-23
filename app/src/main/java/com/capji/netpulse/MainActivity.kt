package com.capji.netpulse

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.capji.netpulse.ui.NetPulseRoot

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark-first app: always light status/nav icons, transparent bars (edge-to-edge).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        handleIntent(intent)
        setContent { NetPulseRoot() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Notification payload keys: url (https only) -> opens the link; tab -> opens that tab; capji://tab/<name> deep link. */
    private fun handleIntent(i: android.content.Intent?) {
        if (i == null) return
        i.getStringExtra("tab")?.let { AppGraph.requestedTab.value = it.uppercase() }
        i.data?.takeIf { it.scheme == "capji" }?.lastPathSegment?.let { AppGraph.requestedTab.value = it.uppercase() }
        i.getStringExtra("url")?.let { com.capji.netpulse.ui.remote.openRemoteLink(this, it) }
    }
}
