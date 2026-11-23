package com.capji.netpulse.data.driver

import com.capji.netpulse.R
import com.capji.netpulse.data.net.Crypto
import com.capji.netpulse.data.net.Macs
import com.capji.netpulse.data.net.Xml
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException

/**
 * Huawei HiLink routers / 4G CPEs (B310, B311, B315, B525, B612, E5573..., usually 192.168.8.1).
 * Login: SHA-256 challenge ("password_type 4") with the session token. Clients: /api/lan/HostInfo.
 * Block: Wi-Fi MAC filter in deny mode (/api/wlan/multi-macfilter-settings) - Wi-Fi devices only.
 */
class HuaweiHiLinkDriver : HomeGatewayDriver() {
    override val brand = "Huawei"
    override val baseCaps = setOf(Capability.BLOCK)
    override val driverNotes = listOf(UiText(R.string.note_wifi_only_block), UiText(R.string.note_hilink_lock))

    private fun errCode(body: String) = Xml.errorCode(body)

    private fun sesTok(): Pair<String, String> {
        val r = http.get(url("/api/webserver/SesTokInfo"))
        val ses = Xml.tag(r.body, "SesInfo")
        val tok = Xml.tag(r.body, "TokInfo")
        if (r.code != 200 || ses == null || tok == null) throw UiTextException(UiText(R.string.err_firmware, listOf("Huawei HiLink")))
        ses.substringAfter("SessionID=", "").takeIf { it.isNotEmpty() }?.let { http.setCookie("SessionID", it) }
        return ses to tok
    }

    /** Fresh CSRF token for a POST (newer firmware: /api/webserver/token, older: SesTokInfo). */
    private fun postToken(): String {
        val r = http.get(url("/api/webserver/token"))
        Xml.tag(r.body, "token")?.let { return it }
        return sesTok().second
    }

    override fun login(): GatewayId {
        val (_, tok) = sesTok()
        val inner = Crypto.b64(Crypto.sha256Hex(pass))
        val pw = Crypto.b64(Crypto.sha256Hex(user + inner + tok))
        val body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Username>$user</Username>" +
            "<Password>$pw</Password><password_type>4</password_type></request>"
        val r = http.postRaw(url("/api/user/login"), body, "application/xml", mapOf("__RequestVerificationToken" to tok))
        when (val code = errCode(r.body)) {
            null -> Unit
            108006, 108007, 108001, 108002 -> throw UiTextException(UiText(R.string.err_wrong_credentials))
            108003 -> Unit // already logged in from this session
            108005 -> throw UiTextException(UiText(R.string.err_login_locked))
            else -> throw UiTextException(UiText(R.string.err_router_code, listOf(code)))
        }
        if (!r.body.contains("OK", ignoreCase = true)) throw UiTextException(UiText(R.string.err_firmware, listOf("Huawei HiLink")))
        val info = runCatching { http.get(url("/api/device/information")).body }.getOrDefault("")
        return GatewayId(
            model = Xml.tag(info, "DeviceName") ?: "Huawei HiLink",
            firmware = Xml.tag(info, "SoftwareVersion") ?: "",
        )
    }

    private fun checkSession(body: String) {
        when (Xml.errorCode(body)) {
            null -> Unit
            125002, 125003, 100003, 108003 -> throw SessionExpired()
            else -> Unit
        }
    }

    override fun readClients(): List<RawClient> {
        val r = http.get(url("/api/lan/HostInfo"))
        checkSession(r.body)
        var hosts = Xml.blocks(r.body, "Host")
        var fromHostList = false
        if (hosts.isEmpty()) {
            val r2 = http.get(url("/api/wlan/host-list"))
            checkSession(r2.body)
            hosts = Xml.blocks(r2.body, "Host")
            fromHostList = true
        }
        return hosts.mapNotNull { h ->
            val mac = Xml.tag(h, "MacAddress")?.let { Macs.norm(it) } ?: return@mapNotNull null
            val active = Xml.tag(h, "Active")
            if (active == "0") return@mapNotNull null
            val iface = (Xml.tag(h, "InterfaceType") ?: "").lowercase()
            val ssid = (Xml.tag(h, "AssociatedSsid") ?: "") + (Xml.tag(h, "Frequency") ?: "")
            val conn = when {
                !fromHostList && (iface.contains("eth") || iface.contains("lan") || iface.contains("usb")) -> ConnectionType.LAN
                ssid.contains("5") -> ConnectionType.WIFI_5
                else -> ConnectionType.WIFI_24
            }
            RawClient(mac, Xml.tag(h, "IpAddress") ?: "", Xml.tag(h, "HostName") ?: "", conn)
        }
    }

    private data class FilterSsid(val index: String, var status: String, val macs: MutableList<String>)

    private fun readFilters(): List<FilterSsid> {
        val r = http.get(url("/api/wlan/multi-macfilter-settings"))
        checkSession(r.body)
        val ssids = Xml.blocks(r.body, "Ssid")
        if (ssids.isEmpty()) throw UiTextException(UiText(R.string.err_firmware, listOf("Huawei HiLink")))
        return ssids.mapIndexed { i, s ->
            val macs = (0..9).mapNotNull { n ->
                Regex("<wifimacfilterMac$n>([^<]*)</wifimacfilterMac$n>", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)
                    ?.let { Macs.norm(it) }
            }.toMutableList()
            FilterSsid(Xml.tag(s, "Index") ?: i.toString(), Xml.tag(s, "WifiMacFilterStatus") ?: "0", macs)
        }
    }

    override fun readBlocked(): Set<String> =
        readFilters().filter { it.status == "2" }.flatMap { it.macs }.toSet()

    override fun writeBlock(mac: String, blocked: Boolean, known: RawClient?) {
        if (blocked && known?.connection == ConnectionType.LAN) throw UiTextException(UiText(R.string.err_wifi_only))
        val filters = readFilters()
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><request><Ssids>")
        for (f in filters) {
            if (blocked) { if (mac !in f.macs) f.macs += mac } else f.macs.remove(mac)
            if (f.macs.size > 10) throw UiTextException(UiText(R.string.err_filter_full))
            val status = if (f.macs.isNotEmpty()) "2" else if (f.status == "2") "0" else f.status
            sb.append("<Ssid><Index>").append(f.index).append("</Index><WifiMacFilterStatus>").append(status).append("</WifiMacFilterStatus>")
            for (n in 0..9) {
                val m = f.macs.getOrNull(n) ?: ""
                sb.append("<wifihostname$n></wifihostname$n><WifiMacFilterMac$n>").append(m).append("</WifiMacFilterMac$n>")
            }
            sb.append("</Ssid>")
        }
        sb.append("</Ssids></request>")
        val r = http.postRaw(
            url("/api/wlan/multi-macfilter-settings"), sb.toString(), "application/xml",
            mapOf("__RequestVerificationToken" to postToken()),
        )
        checkSession(r.body)
        Xml.errorCode(r.body)?.let { throw UiTextException(UiText(R.string.err_router_code, listOf(it))) }
    }
}
