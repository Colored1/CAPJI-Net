package com.capji.netpulse.data.driver

import com.capji.netpulse.R
import com.capji.netpulse.data.net.Crypto
import com.capji.netpulse.data.net.Macs
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException
import org.json.JSONArray
import org.json.JSONObject

/**
 * TP-Link routers. Two web-UI generations are handled:
 *  - LEGACY (TL-WR740N/841N/940N, Archer C5/C7 v1...): "userRpm" pages, Basic cookie auth + random path key.
 *  - LUCI  (Archer C6/C80/C20 v4+, TL-WR840N v6...): /cgi-bin/luci/;stok=... with RSA-encrypted password.
 * Newer AES-encrypted firmware (Deco, AX series) is detected and reported clearly.
 */
class TpLinkDriver : HomeGatewayDriver() {
    override val brand = "TP-Link"
    override val baseCaps = setOf(Capability.BLOCK)
    override val driverNotes = listOf(UiText(R.string.note_wifi_only_block), UiText(R.string.note_experimental_block))

    private var legacyKey: String? = null
    private var stok: String? = null

    override fun login(): GatewayId {
        legacyKey = null; stok = null
        loginLegacy()?.let { return it }
        return loginLuci()
    }

    // ------------------------------------------------------------ legacy

    private fun loginLegacy(): GatewayId? {
        http.setCookie("Authorization", "Basic%20" + Crypto.b64("$user:$pass"))
        val r = http.get(url("/userRpm/LoginRpm.htm?Save=Save"), mapOf("Referer" to url("/")))
        val key = Regex("/([A-Za-z]{16})/userRpm").find(r.body)?.groupValues?.get(1)
        if (key == null) {
            http.reset()
            if (r.code == 401 || r.body.contains("password", true) && r.body.contains("incorrect", true)) {
                throw UiTextException(UiText(R.string.err_wrong_credentials))
            }
            return null
        }
        legacyKey = key
        val model = Regex("(?i)(TL-[A-Z]{2,4}\\d{3,4}[A-Z]*|Archer\\s?[A-Z]?\\d+)").find(r.body)?.value ?: "TP-Link"
        return GatewayId(model)
    }

    private fun lpage(path: String): String {
        val r = http.get(url("/$legacyKey/userRpm/$path"), mapOf("Referer" to url("/$legacyKey/userRpm/Index.htm")))
        if (r.code == 401 || r.code == 403 || r.body.contains("LoginRpm", true) && !r.body.contains("Array")) throw SessionExpired()
        return r.body
    }

    private fun jsArray(body: String, name: String): List<String> {
        val m = Regex("$name\\s*=\\s*new Array\\(([\\s\\S]*?)\\);").find(body) ?: return emptyList()
        return Regex("\"([^\"]*)\"|(-?\\d+)").findAll(m.groupValues[1]).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
    }

    private fun legacyClients(): List<RawClient> {
        val dhcp = jsArray(lpage("AssignedIpAddrListRpm.htm"), "DHCPDynList")
        val wifi = runCatching { Macs.findAll(lpage("WlanStationRpm.htm")).toSet() }.getOrDefault(emptySet())
        return dhcp.chunked(4).filter { it.size >= 3 }.mapNotNull { (name, mac, ip) ->
            val m = Macs.norm(mac) ?: return@mapNotNull null
            RawClient(m, ip, name, if (m in wifi) ConnectionType.WIFI_24 else ConnectionType.LAN)
        }
    }

    private fun legacyBlocked(): Set<String> =
        Macs.findAll(lpage("WlanMacFilterRpm.htm").substringAfter("new Array", "")).toSet()

    private fun legacyWrite(mac: String, blocked: Boolean) {
        val ref = mapOf("Referer" to url("/$legacyKey/userRpm/WlanMacFilterRpm.htm"))
        if (blocked) {
            http.get(url("/$legacyKey/userRpm/WlanMacFilterRpm.htm?Enable=Enable&filterRule=0&Page=1"), ref) // enable + deny list
            http.get(
                url("/$legacyKey/userRpm/WlanMacFilterRpm.htm?Mac=${Macs.dashed(mac)}&Desc=capji&Type=1&entryEnabled=1&Changed=1&SelIndex=0&Page=1&vapIdx=1&Save=Save"),
                ref,
            )
        } else {
            val list = Macs.findAll(lpage("WlanMacFilterRpm.htm").substringAfter("new Array", ""))
            val idx = list.indexOf(mac)
            if (idx >= 0) http.get(url("/$legacyKey/userRpm/WlanMacFilterRpm.htm?Del=$idx&Page=1"), ref)
        }
    }

    // ------------------------------------------------------------ luci

    private fun luci(path: String, form: String, body: String): JSONObject {
        val r = http.postRaw(url("/cgi-bin/luci/;stok=${stok.orEmpty()}$path?form=$form"), body, "application/x-www-form-urlencoded")
        val j = runCatching { JSONObject(r.body) }.getOrNull()
            ?: throw UiTextException(UiText(R.string.err_firmware, listOf(brand)))
        if (!j.optBoolean("success", true) && j.optString("errorcode").contains("timeout", true)) throw SessionExpired()
        if (r.code == 403) throw SessionExpired()
        return j
    }

    private fun loginLuci(): GatewayId {
        val keys = http.postRaw(url("/cgi-bin/luci/;stok=/login?form=keys"), "operation=read", "application/x-www-form-urlencoded")
        val kj = runCatching { JSONObject(keys.body) }.getOrNull()
        val pw = kj?.optJSONObject("data")?.optJSONArray("password")
        if (pw == null || pw.length() < 2) throw UiTextException(UiText(R.string.err_firmware, listOf(brand)))
        val enc = Crypto.rsaHex(pw.getString(0), pw.getString(1), pass)
        val r = http.postRaw(url("/cgi-bin/luci/;stok=/login?form=login"), "operation=login&password=$enc", "application/x-www-form-urlencoded")
        val j = runCatching { JSONObject(r.body) }.getOrNull() ?: throw UiTextException(UiText(R.string.err_firmware, listOf(brand)))
        val s = j.optJSONObject("data")?.optString("stok").orEmpty()
        if (!j.optBoolean("success") || s.isEmpty()) {
            if (j.optString("errorcode").contains("exceed", true)) throw UiTextException(UiText(R.string.err_login_locked))
            throw UiTextException(UiText(R.string.err_wrong_credentials))
        }
        stok = s
        return GatewayId("TP-Link (Archer / Luci)")
    }

    private fun luciList(path: String, form: String, key: String = "data"): JSONArray =
        luci(path, form, "operation=load").optJSONArray(key) ?: JSONArray()

    private fun luciClients(): List<RawClient> {
        val dhcp = luciList("/admin/dhcps", "client")
        val out = ArrayList<RawClient>()
        for (i in 0 until dhcp.length()) {
            val o = dhcp.optJSONObject(i) ?: continue
            val mac = Macs.norm(o.optString("macaddr")) ?: continue
            val wireless = o.optString("wire_type").equals("wireless", true)
            out += RawClient(mac, o.optString("ipaddr"), o.optString("name"), if (wireless) ConnectionType.WIFI_24 else ConnectionType.LAN)
        }
        return out
    }

    private fun luciBlocked(): Set<String> {
        val a = luciList("/admin/access_control", "black_list")
        return (0 until a.length()).mapNotNull { Macs.norm(a.optJSONObject(it)?.optString("mac").orEmpty()) }.toSet()
    }

    private fun luciWrite(mac: String, blocked: Boolean, known: RawClient?) {
        if (blocked) {
            val item = JSONObject().put("mac", Macs.dashed(mac)).put("name", known?.hostname?.ifBlank { "capji" } ?: "capji").put("is_cur_host", "0")
            luci("/admin/access_control", "black", "operation=insert&key=add&index=0&old=add&new=$item")
        } else {
            val a = luciList("/admin/access_control", "black_list")
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (Macs.norm(o.optString("mac")) == mac) {
                    luci("/admin/access_control", "black", "operation=remove&key=${o.optString(".name", "rule_$i")}&index=$i")
                }
            }
        }
    }

    // ------------------------------------------------------------ dispatch

    override fun readClients(): List<RawClient> = if (legacyKey != null) legacyClients() else luciClients()

    override fun readBlocked(): Set<String> = if (legacyKey != null) legacyBlocked() else luciBlocked()

    override fun writeBlock(mac: String, blocked: Boolean, known: RawClient?) {
        if (blocked && known?.connection == ConnectionType.LAN && legacyKey != null) throw UiTextException(UiText(R.string.err_wifi_only))
        if (legacyKey != null) legacyWrite(mac, blocked) else luciWrite(mac, blocked, known)
    }
}
