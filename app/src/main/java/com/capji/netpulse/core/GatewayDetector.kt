package com.capji.netpulse.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address

/**
 * Finds the router's IPv4 address. Tries, in order:
 *  1. the default route of the Wi-Fi network (even when mobile data is the "active" network),
 *  2. the DHCP server address Android got from the router,
 *  3. the first address of the phone's own /24 (x.x.x.1) as a last resort.
 */
object GatewayDetector {
    fun detect(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm != null) {
            val wifi = cm.allNetworks.firstOrNull { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            val props = wifi?.let { cm.getLinkProperties(it) } ?: cm.activeNetwork?.let { cm.getLinkProperties(it) }
            props?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
                ?.takeIf { it != "0.0.0.0" }?.let { return it }
            props?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress?.let { own ->
                fromDhcp(context)?.let { return it }
                return own.substringBeforeLast('.') + ".1"
            }
        }
        return fromDhcp(context)
    }

    @Suppress("DEPRECATION")
    private fun fromDhcp(context: Context): String? = runCatching {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val g = wm.dhcpInfo?.gateway ?: 0
        if (g == 0) null else "${g and 0xff}.${g shr 8 and 0xff}.${g shr 16 and 0xff}.${g shr 24 and 0xff}"
    }.getOrNull()
}
