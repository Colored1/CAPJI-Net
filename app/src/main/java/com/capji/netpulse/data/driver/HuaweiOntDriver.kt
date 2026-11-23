package com.capji.netpulse.data.driver

import com.capji.netpulse.R
import com.capji.netpulse.data.net.Crypto
import com.capji.netpulse.data.net.Macs
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException

/**
 * Huawei fibre ONT / home gateways (HG8145V5, HG8245H, EG8145V5, HG8010H... usually 192.168.100.1 or 192.168.1.1).
 * Login: POST /asp/GetRandCount.asp (token) + POST /login.cgi (password in Base64) -> session cookie.
 * Clients: /html/bbsp/common/GetLanUserDevInfo.asp. Block: MAC filter (verified by reading the list back).
 */
class HuaweiOntDriver : HomeGatewayDriver() {
    override val brand = "Huawei ONT"
    override val baseCaps = setOf(Capability.BLOCK)
    override val driverNotes = listOf(UiText(R.string.note_experimental_block))

    private fun token(): String {
        val t = http.postForm(url("/asp/GetRandCount.asp"), emptyMap()).body.trim().removePrefix("﻿").trim()
        if (t.isEmpty() || t.length > 80 || t.contains("<")) throw UiTextException(UiText(R.string.err_firmware, listOf(brand)))
        return t
    }

    override fun login(): GatewayId {
        http.setCookie("Cookie", "body:Language:english:id=-1")
        val tok = token()
        val r = http.postForm(
            url("/login.cgi"),
            mapOf("UserName" to user, "PassWord" to Crypto.b64(pass), "Language" to "english", "x.X_HW_Token" to tok),
        )
        val cookie = http.cookie("Cookie") ?: ""
        if (!(cookie.contains("sid=") || cookie.contains("sessionID", true))) {
            if (r.body.contains("locked", true) || r.body.contains("ErrorTimes", true) && r.body.contains("3")) {
                throw UiTextException(UiText(R.string.err_login_locked))
            }
            throw UiTextException(UiText(R.string.err_wrong_credentials))
        }
        val info = runCatching { http.get(url("/html/ssmp/deviceinfo/deviceinfo.asp")).body }.getOrDefault("")
        val model = Regex("(HG|EG|HS|WS)[0-9A-Za-z\\-]{3,12}").find(info)?.value ?: "Huawei ONT"
        return GatewayId(model, Regex("V\\d{3}R\\d{3}[A-Z0-9]*").find(info)?.value ?: "")
    }

    private fun page(path: String): String {
        val r = http.get(url(path))
        if (r.code in listOf(401, 403) || (r.body.contains("login.asp", true) && !r.body.contains("USERDevice")) ||
            r.body.contains("txt_Username", true)
        ) throw SessionExpired()
        return r.body
    }

    override fun readClients(): List<RawClient> {
        val body = page("/html/bbsp/common/GetLanUserDevInfo.asp")
        val quoted = Regex("\"([^\"]*)\"")
        return Regex("new USERDevice\\(([^)]*)\\)").findAll(body).mapNotNull { m ->
            val f = quoted.findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
            val mac = f.firstNotNullOfOrNull { Macs.norm(it)?.takeIf { _ -> it.length == 17 } } ?: return@mapNotNull null
            val ip = f.firstOrNull { Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(it) } ?: ""
            val status = f.firstOrNull { it.equals("ONLINE", true) || it.equals("OFFLINE", true) }
            if (status.equals("OFFLINE", true)) return@mapNotNull null
            val port = f.firstOrNull { Regex("(?i)^(SSID|LAN|WLAN)\\d").containsMatchIn(it) } ?: ""
            val conn = when {
                port.startsWith("LAN", true) -> ConnectionType.LAN
                port.contains("5") && port.startsWith("SSID", true) && (port.filter { it.isDigit() }.toIntOrNull() ?: 0) >= 5 -> ConnectionType.WIFI_5
                else -> ConnectionType.WIFI_24
            }
            val skip = setOf("dhcp", "static", "other", "online", "offline", "pppoe", "ipv4", "ipv6")
            val name = f.firstOrNull { s ->
                s.isNotBlank() && s != ip && !s.contains("InternetGatewayDevice") && Macs.norm(s) == null &&
                    s.lowercase() !in skip && !Regex("(?i)^(SSID|LAN|WLAN)\\d").containsMatchIn(s) && !s.all { it.isDigit() }
            } ?: ""
            RawClient(mac, ip, name, conn)
        }.toList()
    }

    private val filterBase = "InternetGatewayDevice.X_HW_Security.MacFilter"
    private val filterPage = "html/bbsp/macfilter/macfilter.asp"

    private data class Entry(val domain: String, val mac: String)

    private fun entries(): List<Entry> {
        val body = page("/$filterPage")
        return Regex("new MacFilter\\(\"([^\"]*)\"([^)]*)\\)").findAll(body).mapNotNull { m ->
            val mac = Macs.findAll(m.groupValues[2]).firstOrNull() ?: Macs.findAll(m.value).firstOrNull() ?: return@mapNotNull null
            Entry(m.groupValues[1], mac)
        }.toList()
    }

    override fun readBlocked(): Set<String> = entries().map { it.mac }.toSet()

    override fun writeBlock(mac: String, blocked: Boolean, known: RawClient?) {
        if (blocked) {
            // make sure filtering is switched on in "blacklist" mode, then add the MAC
            http.postForm(
                url("/html/bbsp/macfilter/set.cgi?x=InternetGatewayDevice.X_HW_Security&RequestFile=$filterPage"),
                mapOf("x.MacFilterRight" to "1", "x.MacFilterPolicy" to "0", "x.X_HW_Token" to token()),
            )
            http.postForm(
                url("/html/bbsp/macfilter/add.cgi?x=$filterBase&RequestFile=$filterPage"),
                mapOf("x.SourceMACAddress" to mac, "x.X_HW_Token" to token()),
            )
        } else {
            entries().filter { it.mac == mac }.forEach { e ->
                http.postForm(
                    url("/html/bbsp/macfilter/del.cgi?x=${e.domain}&RequestFile=$filterPage"),
                    mapOf("x.X_HW_Token" to token()),
                )
            }
        }
    }
}
