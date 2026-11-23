package com.capji.netpulse.core

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.content.Context
import com.capji.netpulse.data.net.Diag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Extra device names without any router access: SSDP/UPnP answers (TVs, consoles, speakers, some phones) and
 * mDNS/Bonjour services via NsdManager (iPhones, Macs, Chromecasts, AirPlay). Used only to fill in names the
 * router did not give; passive, standard multicast discovery (no spoofing, no root).
 */
object NameDiscovery {
    suspend fun discover(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
        val out = ConcurrentHashMap<String, String>()
        runCatching { ssdp().forEach { (ip, n) -> out[ip] = n } }.onFailure { Diag.add("ssdp failed: ${it.message}") }
        runCatching { mdns(context).forEach { (ip, n) -> out.putIfAbsent(ip, n) } }.onFailure { Diag.add("mdns failed: ${it.message}") }
        Diag.add("name discovery: ${out.size} names")
        out
    }

    private fun ssdp(): Map<String, String> {
        val found = HashMap<String, String>()
        val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n").toByteArray()
        DatagramSocket().use { s ->
            s.soTimeout = 700
            s.send(DatagramPacket(msg, msg.size, InetAddress.getByName("239.255.255.250"), 1900))
            val end = System.currentTimeMillis() + 2500
            val locations = HashMap<String, String>()
            val buf = ByteArray(2048)
            while (System.currentTimeMillis() < end) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    val text = String(p.data, 0, p.length)
                    val ip = p.address.hostAddress ?: continue
                    Regex("(?i)LOCATION:\\s*(\\S+)").find(text)?.groupValues?.get(1)?.let { locations.putIfAbsent(ip, it) }
                    Regex("(?i)SERVER:\\s*(.+)").find(text)?.groupValues?.get(1)?.trim()?.let { found.putIfAbsent(ip, it.take(40)) }
                } catch (_: SocketTimeoutException) { }
            }
            for ((ip, loc) in locations.entries.take(25)) {
                runCatching {
                    val c = URL(loc).openConnection().apply { connectTimeout = 600; readTimeout = 800 }
                    val x = c.getInputStream().bufferedReader().readText().take(6000)
                    val name = Regex("<friendlyName>([^<]+)</friendlyName>").find(x)?.groupValues?.get(1)
                        ?: Regex("<modelName>([^<]+)</modelName>").find(x)?.groupValues?.get(1)
                    if (name != null) found[ip] = name
                }
            }
        }
        return found
    }

    private suspend fun mdns(context: Context): Map<String, String> {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val names = ConcurrentHashMap<String, String>()
        val listeners = ArrayList<NsdManager.DiscoveryListener>()
        val pending = java.util.concurrent.ConcurrentLinkedQueue<NsdServiceInfo>()
        for (type in listOf("_companion-link._tcp.", "_airplay._tcp.", "_googlecast._tcp.", "_device-info._tcp.", "_workstation._tcp.")) {
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) = Unit
                override fun onServiceFound(i: NsdServiceInfo) { pending.add(i) }
                override fun onServiceLost(i: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(t: String) = Unit
                override fun onStartDiscoveryFailed(t: String, e: Int) = Unit
                override fun onStopDiscoveryFailed(t: String, e: Int) = Unit
            }
            runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l); listeners += l }
        }
        delay(3500)
        val seen = HashSet<String>()
        while (true) {
            val info = pending.poll() ?: break
            if (!seen.add(info.serviceName)) continue
            var done = false
            runCatching {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(i: NsdServiceInfo, e: Int) { done = true }
                    @Suppress("DEPRECATION")
                    override fun onServiceResolved(i: NsdServiceInfo) {
                        i.host?.hostAddress?.let { names.putIfAbsent(it, i.serviceName.substringBefore('@').substringAfter('.').ifBlank { i.serviceName }) }
                        done = true
                    }
                })
            }
            var waited = 0
            while (!done && waited < 700) { delay(50); waited += 50 }
        }
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        return names
    }
}
