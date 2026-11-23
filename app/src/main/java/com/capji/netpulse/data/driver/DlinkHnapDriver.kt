package com.capji.netpulse.data.driver

import com.capji.netpulse.R
import com.capji.netpulse.data.net.Macs
import com.capji.netpulse.data.net.Xml
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * D-Link routers with the HNAP1 API (DIR-8xx, DIR-18xx, DIR-X..., COVR). HMAC-MD5 challenge login,
 * clients from GetClientInfo, block/unblock through the MAC filter (SetMACFilters2, deny-list mode).
 * Older D-Link models without HNAP use the "Router page assistant" (WebView) instead.
 */
class DlinkHnapDriver : HomeGatewayDriver() {
    override val brand = "D-Link"
    override val baseCaps = setOf(Capability.BLOCK)
    override val driverNotes = listOf(UiText(R.string.note_experimental_block))

    private var privateKey = ""
    private val ns = "http://purenetworks.com/HNAP1/"

    private fun hmac(key: String, msg: String): String {
        val m = Mac.getInstance("HmacMD5")
        m.init(SecretKeySpec(key.toByteArray(), "HmacMD5"))
        return m.doFinal(msg.toByteArray()).joinToString("") { "%02X".format(it) }
    }

    private fun soap(action: String, inner: String, authed: Boolean = true): String {
        val body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
            "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" +
            "<$action xmlns=\"$ns\">$inner</$action></soap:Body></soap:Envelope>"
        val ts = (System.currentTimeMillis() / 1000 % 2000000000).toString()
        val soapAction = "\"$ns$action\""
        val auth = hmac(if (authed) privateKey else "withoutloginkey", ts + soapAction) + " " + ts
        val r = http.postRaw(
            url("/HNAP1/"), body, "text/xml; charset=utf-8",
            mapOf("SOAPAction" to soapAction, "HNAP_AUTH" to auth),
        )
        if (r.code == 401 || r.code == 403 || r.body.contains("ERROR", true) && authed && !r.body.contains("<${action}Result>OK")) throw SessionExpired()
        return r.body
    }

    override fun login(): GatewayId {
        val c = soap("Login", "<Action>request</Action><Username>$user</Username><LoginPassword></LoginPassword><Captcha></Captcha>", authed = false)
        val challenge = Xml.tag(c, "Challenge")
        val pub = Xml.tag(c, "PublicKey")
        val cookie = Xml.tag(c, "Cookie")
        if (challenge == null || pub == null || cookie == null) throw UiTextException(UiText(R.string.err_firmware, listOf("D-Link HNAP")))
        privateKey = hmac(pub + pass, challenge)
        http.setCookie("uid", cookie)
        val pw = hmac(privateKey, challenge)
        val r = soap("Login", "<Action>login</Action><Username>$user</Username><LoginPassword>$pw</LoginPassword><Captcha></Captcha>")
        if (!r.contains("<LoginResult>success", true)) throw UiTextException(UiText(R.string.err_wrong_credentials))
        val info = runCatching { soap("GetDeviceSettings", "") }.getOrDefault("")
        return GatewayId(Xml.tag(info, "ModelName") ?: "D-Link", Xml.tag(info, "FirmwareVersion") ?: "")
    }

    override fun readClients(): List<RawClient> {
        val r = soap("GetClientInfo", "")
        return Xml.blocks(r, "ClientInfo").mapNotNull { c ->
            val mac = Macs.norm(Xml.tag(c, "MacAddress") ?: "") ?: return@mapNotNull null
            val type = (Xml.tag(c, "Type") ?: "").lowercase()
            val conn = when {
                type.contains("5") -> ConnectionType.WIFI_5
                type.contains("wifi") || type.contains("wireless") || type.contains("2.4") -> ConnectionType.WIFI_24
                else -> ConnectionType.LAN
            }
            RawClient(mac, Xml.tag(c, "IPv4Address") ?: "", Xml.tag(c, "DeviceName") ?: Xml.tag(c, "NickName") ?: "", conn)
        }
    }

    private fun filterList(): Pair<Boolean, List<Pair<String, String>>> {
        val r = soap("GetMACFilters2", "")
        val allow = Xml.tag(r, "IsAllowList")?.equals("true", true) == true
        val macs = Xml.blocks(r, "MACInfo").mapNotNull { b ->
            Macs.norm(Xml.tag(b, "MacAddress") ?: "")?.let { it to (Xml.tag(b, "DeviceName") ?: "") }
        }
        return allow to macs
    }

    override fun readBlocked(): Set<String> {
        val (allow, macs) = filterList()
        return if (allow) emptySet() else macs.map { it.first }.toSet()
    }

    override fun writeBlock(mac: String, blocked: Boolean, known: RawClient?) {
        val macs = filterList().second.toMutableList()
        if (blocked) { if (macs.none { it.first == mac }) macs += mac to (known?.hostname ?: "capji") } else macs.removeAll { it.first == mac }
        val list = macs.joinToString("") { "<MACInfo><MacAddress>${it.first}</MacAddress><DeviceName>${it.second.ifBlank { "device" }}</DeviceName></MACInfo>" }
        val r = soap("SetMACFilters2", "<IsEnabled>${macs.isNotEmpty()}</IsEnabled><IsAllowList>false</IsAllowList><MACList>$list</MACList>")
        if (!r.contains("OK", true)) throw UiTextException(UiText(R.string.err_rule_rejected))
        runCatching { soap("SetTriggerWirelessSettings", "") } // some firmwares apply on this call; ignore if unsupported
    }
}
