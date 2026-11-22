package com.capji.netpulse.core

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

/** Finds the default IPv4 gateway of the active network (needs only ACCESS_NETWORK_STATE). */
object GatewayDetector {
    fun detect(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = cm.activeNetwork ?: return null
        val props = cm.getLinkProperties(network) ?: return null
        return props.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
    }
}
