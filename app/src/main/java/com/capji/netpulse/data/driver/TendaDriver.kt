package com.capji.netpulse.data.driver

import com.capji.netpulse.R
import com.capji.netpulse.data.net.Crypto
import com.capji.netpulse.data.net.Macs
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException
import org.json.JSONObject

/**
 * Tenda AC-series routers (AC5/AC6/AC10/AC1200..., usually 192.168.0.1). Login: POST /login/Auth with the
 * MD5 of the password. Clients: /goform/getQos (onlineList). Block: /goform/setMacFilter in black-list mode.
 * Rule verification reads the filter back; if the firmware differs the app says so and offers the router-page assistant.
 */
class TendaDriver : HomeGatewayDriver() {
    override val brand = "Tenda"
    override val baseCaps = setOf(Capability.BLOCK)
    override val driverNotes = listOf(UiText(R.string.note_experimental_block))

    private fun rnd() = "0." + (10000000..99999999).random()

    override fun login(): GatewayId {
        val md5 = Crypto.md5Hex(pass)
        http.setCookie("password", md5)
        val r = http.postForm(url("/login/Auth"), mapOf("username" to user, "password" to md5), mapOf("Referer" to url("/login.html")))
        if (r.code >= 400 || r.body.contains("login.html", true) && r.body.contains("error", true)) {
            throw UiTextException(UiText(R.string.err_wrong_credentials))
        }
        val probe = http.get(url("/goform/getQos?random=${rnd()}&modules=onlineList"))
        if (!probe.body.contains("onlineList")) throw UiTextException(UiText(R.string.err_firmware, listOf("Tenda")))
        return GatewayId("Tenda")
    }

    override fun readClients(): List<RawClient> {
        val r = http.get(url("/goform/getQos?random=${rnd()}&modules=onlineList"))
        if (!r.body.contains("onlineList")) throw SessionExpired()
        val arr = runCatching { JSONObject(r.body).optJSONArray("onlineList") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val mac = Macs.norm(o.optString("qosListMac")) ?: return@mapNotNull null
            val type = o.optString("qosListConnectType").lowercase()
            RawClient(mac, o.optString("qosListIP"), o.optString("qosListHostname").ifBlank { o.optString("qosListRemark") },
                if (type.contains("wire") && !type.contains("wireless") || type.contains("lan")) ConnectionType.LAN else ConnectionType.WIFI_24)
        }
    }

    private fun filterState(): Pair<String, List<String>> {
        val r = http.get(url("/goform/getMacFilter?random=${rnd()}&modules=macFilter"))
        val j = runCatching { JSONObject(r.body) }.getOrNull() ?: throw UiTextException(UiText(R.string.err_rule_rejected))
        val o = j.optJSONObject("macFilter") ?: j
        val type = o.optString("macFilterType", "black")
        return type to Macs.findAll(o.optString("deviceList"))
    }

    override fun readBlocked(): Set<String> {
        val (type, macs) = filterState()
        return if (type == "black") macs.toSet() else emptySet()
    }

    override fun writeBlock(mac: String, blocked: Boolean, known: RawClient?) {
        val macs = filterState().second.toMutableList()
        if (blocked) { if (mac !in macs) macs += mac } else macs.remove(mac)
        val list = macs.joinToString("\n") { "${known?.hostname?.ifBlank { "device" } ?: "device"}\t$it" }
        val r = http.postForm(url("/goform/setMacFilter"), mapOf("macFilterType" to "black", "deviceList" to list))
        if (r.code >= 400) throw UiTextException(UiText(R.string.err_rule_rejected))
    }
}
