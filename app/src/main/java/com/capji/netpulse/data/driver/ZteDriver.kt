package com.capji.netpulse.data.driver

import com.capji.netpulse.R
import com.capji.netpulse.data.net.Crypto
import com.capji.netpulse.data.net.Macs
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException

/**
 * ZTE home gateways with the "getpage.gch" web UI (ZXHN F660, F670L, F680, F609, H298...).
 * Login: token from the login page + SHA-256(password + token) (older firmware: plain password).
 * Clients: DHCP lease table + Wi-Fi association tables (2.4 / 5 GHz).
 * Blocking is NOT exposed by a stable URL on ZTE firmware, so this driver is monitoring-first; the app
 * explains this and offers the router page. Use "Router diagnostics" to send me your firmware's pages.
 */
class ZteDriver : HomeGatewayDriver() {
    override val brand = "ZTE"
    override val baseCaps: Set<Capability> = setOf(Capability.BLOCK)
    override val driverNotes = listOf(UiText(R.string.note_zte_block))

    override fun login(): GatewayId {
        val first = http.get(url("/"))
        val tok = Regex("Frm_Logintoken\"\\)\\.value\\s*=\\s*\"(\\d+)\"").find(first.body)?.groupValues?.get(1)
            ?: Regex("Frm_Logintoken[^0-9]{1,40}(\\d{3,})").find(first.body)?.groupValues?.get(1)
        if (tok == null) throw UiTextException(UiText(R.string.err_firmware, listOf("ZTE")))
        val usesSha = first.body.contains("sha256", ignoreCase = true)
        val value = if (usesSha) Crypto.sha256Hex(pass + tok) else pass
        val r = http.postForm(
            url("/"),
            mapOf("frashnum" to "", "action" to "login", "Frm_Logintoken" to tok, "Username" to user, "Password" to value),
        )
        if (r.body.contains("Frm_Logintoken") || r.body.contains("Frm_Password")) {
            throw UiTextException(UiText(R.string.err_wrong_credentials))
        }
        val title = Regex("(?i)(ZXHN\\s*)?\\b(F\\d{3}[A-Z0-9]*|H\\d{3}[A-Z]?|MF\\d{2,4}[A-Z]?)\\b").find(first.body)?.value
        return GatewayId(title?.trim() ?: "ZTE Gateway")
    }

    private fun page(nextPage: String): String {
        val r = http.get(url("/getpage.gch?pid=1002&nextpage=$nextPage"))
        if (r.body.contains("Frm_Logintoken") || r.body.contains("Frm_Password")) throw SessionExpired()
        return r.body
    }

    private fun instances(body: String): List<Map<String, String>> =
        Regex("<Instance>([\\s\\S]*?)</Instance>").findAll(body).map { inst ->
            val names = Regex("<ParaName>([^<]*)</ParaName>").findAll(inst.groupValues[1]).map { it.groupValues[1] }.toList()
            val values = Regex("<ParaValue>([^<]*)</ParaValue>").findAll(inst.groupValues[1]).map { it.groupValues[1] }.toList()
            names.zip(values).toMap()
        }.toList()

    override fun readClients(): List<RawClient> {
        val wifi24 = runCatching { Macs.findAll(page("net_wlanm_assoc1_t.gch")).toSet() }.getOrDefault(emptySet())
        val wifi5 = runCatching { Macs.findAll(page("net_wlanm_assoc2_t.gch")).toSet() }.getOrDefault(emptySet())
        val dhcp = instances(page("net_dhcp_dynamic_t.gch"))
        val out = dhcp.mapNotNull { m ->
            val mac = (m["MACAddr"] ?: m["MACAddress"] ?: m["PhyAddress"] ?: return@mapNotNull null).let { Macs.norm(it) } ?: return@mapNotNull null
            val conn = when (mac) { in wifi5 -> ConnectionType.WIFI_5; in wifi24 -> ConnectionType.WIFI_24; else -> ConnectionType.LAN }
            RawClient(mac, m["IPAddr"] ?: m["IPAddress"] ?: "", m["HostName"] ?: "", conn)
        }
        return out
    }

    // ------------------------------------------------------------ MAC filter (discovered from the router's own pages)

    private var macPage: String? = null
    private var tokenRegexes = listOf(
        Regex("_sessionTOKEN\"?\\s*[:=,]\\s*\"?([A-Za-z0-9]{6,})"),
        Regex("session_token\\s*=\\s*\"([^\"]+)\""),
        Regex("name=\"_sessionTOKEN\"[^>]*value=\"([^\"]+)\""),
    )

    /** Crawls the logged-in UI for a MAC-filter page (names differ between ZTE firmwares). */
    private fun findMacPage(): String {
        macPage?.let { return it }
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        queue += "/"
        var fetched = 0
        val nextRx = Regex("nextpage=([A-Za-z0-9_.]+\\.gch)")
        val srcRx = Regex("(?:src|href)=\"([^\"]+)\"")
        while (queue.isNotEmpty() && fetched < 14) {
            val u = queue.removeFirst()
            fetched++
            val body = runCatching { http.get(url(u)).body }.getOrDefault("")
            nextRx.findAll(body).forEach { seen += it.groupValues[1] }
            srcRx.findAll(body).map { it.groupValues[1] }.filter { it.contains("getpage.gch") || it.endsWith(".ghtml") || it.endsWith(".gch") }
                .map { if (it.startsWith("/")) it else "/$it" }.take(6).forEach { if (fetched + queue.size < 14) queue += it }
        }
        val cands = seen.filter { n -> val l = n.lowercase(); l.contains("mac") && (l.contains("filter") || l.contains("fltr") || l.contains("secu") || l.contains("acl")) }
        for (c in cands) {
            val body = runCatching { page(c) }.getOrDefault("")
            if (body.contains("MAC", ignoreCase = true)) { macPage = c; return c }
        }
        throw UiTextException(UiText(R.string.err_rule_rejected))
    }

    override fun readBlocked(): Set<String> {
        val pg = runCatching { findMacPage() }.getOrNull() ?: return emptySet()
        return instances(page(pg)).flatMap { m -> m.values.mapNotNull { Macs.norm(it) } }.toSet()
    }

    override fun writeBlock(mac: String, blocked: Boolean, known: RawClient?) {
        val pg = findMacPage()
        val body = page(pg)
        val token = tokenRegexes.firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1) }
        val inst = instances(body)
        val field = inst.firstOrNull()?.keys?.firstOrNull { it.contains("MAC", true) } ?: "MACAddr"
        val url = url("/getpage.gch?pid=1002&nextpage=$pg")
        if (blocked) {
            val f = linkedMapOf("IF_ACTION" to "new", "_InstID" to "-1", field to mac, "Enable" to "1")
            token?.let { f["_sessionTOKEN"] = it }
            http.postForm(url, f)
        } else {
            val ids = Regex("<Instance>([\\s\\S]*?)</Instance>").findAll(body).filter { it.value.contains(mac, true) }
                .mapNotNull { Regex("<ParaName>_InstID</ParaName><ParaValue>([^<]*)</ParaValue>").find(it.value)?.groupValues?.get(1) }.toList()
            for (id in ids) {
                val f = linkedMapOf("IF_ACTION" to "delete", "_InstID" to id)
                token?.let { f["_sessionTOKEN"] = it }
                http.postForm(url, f)
            }
        }
    }
}
