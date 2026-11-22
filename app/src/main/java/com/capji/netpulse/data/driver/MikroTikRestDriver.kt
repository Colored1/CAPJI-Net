package com.capji.netpulse.data.driver

import com.capji.netpulse.domain.driver.RouterDriver
import com.capji.netpulse.domain.identify.DeviceIdentifier
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.domain.model.RouterInfo
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.SpeedLimit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * EXPERIMENTAL driver for MikroTik RouterOS 7 using the built-in REST API (enable the `www` service).
 *  - clients      : /ip/dhcp-server/lease (+ /interface/wifi/registration-table for the band)
 *  - block        : address-list "netpulse-blocked" + one forward DROP rule (created on connect)
 *  - speed limit  : /queue/simple named "netpulse-<MAC>"
 *  - quota / pause: not implemented here (needs a RouterOS script/scheduler) -> reported as unsupported
 * Not verified against real hardware yet: treat as a reference implementation of the driver contract.
 */
class MikroTikRestDriver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) : RouterDriver {

    override val capabilities = setOf(Capability.BLOCK, Capability.SPEED_LIMIT)
    override val events: Flow<String> = emptyFlow()

    private var base = ""
    private var auth = ""
    private var cache: List<NetDevice> = emptyList()
    private val jsonType = "application/json".toMediaType()

    private fun call(method: String, path: String, body: JSONObject? = null): String {
        val rb = body?.toString()?.toRequestBody(jsonType)
        val req = Request.Builder().url(base + path).header("Authorization", auth).method(method, rb).build()
        return client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            check(r.isSuccessful) { "HTTP ${r.code}: ${text.take(120)}" }
            text
        }
    }

    private fun array(path: String) = JSONArray(call("GET", path))

    override suspend fun connect(profile: RouterProfile, password: String): Result<RouterInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                base = "http://${profile.host}/rest"
                auth = Credentials.basic(profile.username, password)
                val res = JSONObject(call("GET", "/system/resource"))
                if (array("/ip/firewall/filter?comment=$RULE").length() == 0) {
                    call(
                        "PUT", "/ip/firewall/filter",
                        JSONObject().put("chain", "forward").put("action", "drop")
                            .put("src-address-list", LIST).put("comment", RULE),
                    )
                }
                RouterInfo(
                    model = res.optString("board-name", "MikroTik"),
                    firmware = "RouterOS " + res.optString("version"),
                    gateway = profile.host,
                    capabilities = capabilities,
                    notes = listOf(
                        "Live per-device speed is read from the device's queue; devices without a queue show 0.",
                        "Quota and timed pause need a RouterOS script/scheduler and are not available in this driver yet.",
                        "Rules are keyed by IP: a changed DHCP lease or a randomized MAC can bypass them.",
                    ),
                )
            }
        }

    override fun observeDevices(): Flow<List<NetDevice>> = flow {
        while (true) {
            runCatching { fetch() }.onSuccess { cache = it; emit(it) }
            delay(3000)
        }
    }.flowOn(Dispatchers.IO)

    private fun fetch(): List<NetDevice> {
        val leases = array("/ip/dhcp-server/lease")
        val blockedIps = array("/ip/firewall/address-list?list=$LIST").let { a ->
            (0 until a.length()).map { a.getJSONObject(it).optString("address") }.toSet()
        }
        val queues = array("/queue/simple").let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("name").startsWith(QPREFIX) }
        }
        val wifi = runCatching { array("/interface/wifi/registration-table") }.getOrNull()

        return (0 until leases.length()).map { leases.getJSONObject(it) }
            .filter { it.optString("status") == "bound" }
            .map { l ->
                val mac = l.optString("mac-address").uppercase()
                val ip = l.optString("active-address").ifBlank { l.optString("address") }
                val host = l.optString("host-name")
                val id = DeviceIdentifier.identify(mac, host)
                val q = queues.firstOrNull { it.optString("target").startsWith("$ip/") }
                val rate = q?.optString("rate")?.split("/")
                NetDevice(
                    mac = mac, hostname = host, vendor = id.vendor, model = id.model, kind = id.kind,
                    ip = ip, connection = connFor(mac, wifi), randomizedMac = id.randomized,
                    sessionDownMb = 0, sessionUpMb = 0,
                    liveDownKbps = rate?.getOrNull(1)?.toLongOrNull()?.div(1000)?.toInt() ?: 0,
                    liveUpKbps = rate?.getOrNull(0)?.toLongOrNull()?.div(1000)?.toInt() ?: 0,
                    blocked = ip in blockedIps,
                    limit = q?.let { parseLimit(it) },
                )
            }
    }

    private fun connFor(mac: String, wifi: JSONArray?): ConnectionType {
        if (wifi == null) return ConnectionType.LAN
        for (i in 0 until wifi.length()) {
            val e = wifi.getJSONObject(i)
            if (e.optString("mac-address").equals(mac, ignoreCase = true)) {
                val band = e.optString("band") + e.optString("interface")
                return if (band.contains("2g", true) || band.contains("2.4")) ConnectionType.WIFI_24 else ConnectionType.WIFI_5
            }
        }
        return ConnectionType.LAN
    }

    private fun toKbps(s: String): Int {
        val n = s.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        return when (s.lastOrNull()) {
            'M' -> n * 1000
            'G' -> n * 1_000_000
            'k', 'K' -> n
            else -> n / 1000
        }
    }

    private fun parseLimit(q: JSONObject): SpeedLimit? {
        val parts = q.optString("max-limit").split("/")
        if (parts.size != 2) return null
        val up = toKbps(parts[0])
        val down = toKbps(parts[1])
        return if (up == 0 && down == 0) null else SpeedLimit(downKbps = down, upKbps = up)
    }

    private suspend fun io(block: () -> Unit): Result<Unit> = withContext(Dispatchers.IO) { runCatching(block) }

    private fun deviceOf(mac: String) = cache.firstOrNull { it.mac == mac } ?: error("Unknown device")

    override suspend fun refresh() = Unit

    override suspend fun setBlocked(mac: String, blocked: Boolean): Result<Unit> = io {
        val d = deviceOf(mac)
        if (blocked) {
            call(
                "PUT", "/ip/firewall/address-list",
                JSONObject().put("list", LIST).put("address", d.ip).put("comment", mac),
            )
        } else {
            val found = array("/ip/firewall/address-list?list=$LIST&address=${d.ip}")
            for (i in 0 until found.length()) call("DELETE", "/ip/firewall/address-list/" + found.getJSONObject(i).getString(".id"))
        }
    }

    override suspend fun setLimit(mac: String, limit: SpeedLimit?): Result<Unit> = io {
        val d = deviceOf(mac)
        val name = QPREFIX + mac
        val existing = array("/queue/simple?name=$name")
        val id = if (existing.length() > 0) existing.getJSONObject(0).getString(".id") else null
        if (limit == null) {
            if (id != null) call("DELETE", "/queue/simple/$id")
        } else {
            val body = JSONObject().put("name", name).put("target", "${d.ip}/32")
                .put("max-limit", "${limit.upKbps}k/${limit.downKbps}k")
            if (id != null) call("PATCH", "/queue/simple/$id", body) else call("PUT", "/queue/simple", body)
        }
    }

    override suspend fun setQuota(mac: String, quota: DataQuota?): Result<Unit> =
        Result.failure(UnsupportedOperationException("Quota isn't supported by the MikroTik driver yet"))

    override suspend fun pause(mac: String, minutes: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException("Timed pause isn't supported by the MikroTik driver yet"))

    override fun disconnect() = Unit

    private companion object {
        const val LIST = "netpulse-blocked"
        const val RULE = "netpulse-block-rule"
        const val QPREFIX = "netpulse-"
    }
}
