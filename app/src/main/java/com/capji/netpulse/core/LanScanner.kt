package com.capji.netpulse.core

import android.content.Context
import android.net.ConnectivityManager
import com.capji.netpulse.data.net.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Finds devices on a /24 without any router login: ping sweep + ARP table + reverse DNS.
 * Android 10+ hides /proc/net/arp from normal apps, so MAC addresses may be missing (devices are then shown by IP).
 */
object LanScanner {
    data class Found(val ip: String, val mac: String?, val host: String?)
    data class Subnet(val base: String, val selfIp: String)

    fun wifiSubnet(context: Context): Subnet? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val props = cm.getLinkProperties(cm.activeNetwork ?: return null) ?: return null
        val a = props.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val ip = a.address.hostAddress ?: return null
        return Subnet(ip.substringBeforeLast(".") + ".", ip)
    }

    /** The phone's own hotspot interface (ap0 / swlan0 / wlan1...), when Android lets the app see it. */
    fun hotspotSubnet(): Subnet? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .filter { n -> n.name.startsWith("ap") || n.name.startsWith("swlan") || n.name.startsWith("softap") || n.name == "wlan1" || n.name.startsWith("rndis") }
            .flatMap { n -> n.inetAddresses.toList().filterIsInstance<Inet4Address>() }
            .firstOrNull()?.hostAddress
    }.getOrNull()?.let { Subnet(it.substringBeforeLast(".") + ".", it) }

    fun readArp(): Map<String, String> = runCatching {
        File("/proc/net/arp").readLines().drop(1).mapNotNull { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size >= 4 && p[3] != "00:00:00:00:00:00" && p[2] != "0x0") p[0] to p[3].uppercase() else null
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun alive(ip: String): Boolean {
        if (runCatching { InetAddress.getByName(ip).isReachable(350) }.getOrDefault(false)) return true
        for (port in intArrayOf(80, 443, 53)) {
            val ok = runCatching { Socket().use { it.connect(InetSocketAddress(ip, port), 150); true } }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    suspend fun scan(subnet: Subnet): List<Found> = withContext(Dispatchers.IO) {
        val sem = Semaphore(48)
        val up = coroutineScope {
            (1..254).map { n ->
                async { sem.withPermit { "${subnet.base}$n".takeIf { alive(it) } } }
            }.awaitAll().filterNotNull()
        }
        val arp = readArp()
        val ips = (up + arp.keys.filter { it.startsWith(subnet.base) }).distinct().filter { it != subnet.selfIp }
        val found = coroutineScope {
            ips.map { ip ->
                async {
                    val host = withTimeoutOrNull(700) { runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrNull() }
                        ?.takeIf { it != ip }
                    Found(ip, arp[ip], host)
                }
            }.awaitAll()
        }
        Diag.add("LAN scan ${subnet.base}x -> ${found.size} devices, ${arp.size} arp entries")
        found
    }
}
