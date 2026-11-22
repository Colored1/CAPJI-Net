package com.capji.netpulse.data.driver

import com.capji.netpulse.domain.driver.RouterDriver
import com.capji.netpulse.domain.identify.DeviceIdentifier
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.domain.model.QuotaAction
import com.capji.netpulse.domain.model.RouterInfo
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.SpeedLimit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random

/** Fully simulated router so the whole UI can be tested without hardware. Any non-empty password works. */
class MockRouterDriver : RouterDriver {

    override val capabilities = setOf(Capability.BLOCK, Capability.SPEED_LIMIT, Capability.QUOTA, Capability.PAUSE)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val events: Flow<String> get() = _events

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val devices = MutableStateFlow<List<NetDevice>>(emptyList())
    private val baseRate = mutableMapOf<String, Int>()
    private val frac = mutableMapOf<String, Double>()
    private var ticker: Job? = null

    override suspend fun connect(profile: RouterProfile, password: String): Result<RouterInfo> {
        delay(1300)
        if (password.isBlank()) return Result.failure(IllegalArgumentException("Wrong username or password"))
        devices.value = seed()
        startTicker()
        return Result.success(
            RouterInfo(
                model = "NetPulse Demo Router",
                firmware = "sim-1.0",
                gateway = profile.host.ifBlank { "192.168.88.1" },
                capabilities = capabilities,
                notes = listOf(
                    "Demo router: every number is simulated (usage runs ~10x faster so quotas fill quickly).",
                    "On real routers without native quota support, caps are counted by NetPulse and only enforced while the app/service runs.",
                    "Devices with private (randomized) MACs can reappear as new devices and escape MAC-based rules.",
                ),
            ),
        )
    }

    override fun observeDevices(): Flow<List<NetDevice>> = devices

    override suspend fun refresh() {
        delay(2400)
        devices.update { list ->
            if (list.any { it.mac == GUEST_MAC }) list.filterNot { it.mac == GUEST_MAC }
            else list + mk(GUEST_MAC, "Pixel-8", "192.168.88.77", ConnectionType.WIFI_5, 3100)
        }
    }

    override suspend fun setBlocked(mac: String, blocked: Boolean) = mutate(mac) { it.copy(blocked = blocked) }

    override suspend fun setLimit(mac: String, limit: SpeedLimit?) = mutate(mac) { it.copy(limit = limit) }

    override suspend fun setQuota(mac: String, quota: DataQuota?) = mutate(mac) { it.copy(quota = quota) }

    override suspend fun pause(mac: String, minutes: Int) = mutate(mac) {
        it.copy(pausedUntilMs = if (minutes <= 0) null else System.currentTimeMillis() + minutes * 60_000L)
    }

    override fun disconnect() {
        ticker?.cancel()
        scope.cancel()
        devices.value = emptyList()
    }

    // ---------------- simulation ----------------

    private suspend fun mutate(mac: String, f: (NetDevice) -> NetDevice): Result<Unit> {
        delay(300)
        devices.update { list -> list.map { if (it.mac == mac) f(it) else it } }
        return Result.success(Unit)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                devices.update { list -> list.map { tick(it, now) } }
            }
        }
    }

    private fun grow(key: String, mb: Double): Long {
        val v = (frac[key] ?: 0.0) + mb
        val whole = v.toLong()
        frac[key] = v - whole
        return whole
    }

    private fun tick(src: NetDevice, now: Long): NetDevice {
        var d = src
        val p = d.pausedUntilMs
        if (p != null && p <= now) d = d.copy(pausedUntilMs = null)

        val q = d.quota
        val cut = d.blocked || d.pausedUntilMs != null || (q != null && q.reached && q.action == QuotaAction.BLOCK)
        var limit = d.limit
        if (q != null && q.reached && q.action == QuotaAction.THROTTLE && limit == null) limit = SpeedLimit(256, 128)

        val base = baseRate[d.mac] ?: 2500
        var down = if (cut) 0 else (base * (0.7 + Random.nextDouble() * 0.6)).toInt()
        var up = (down * 0.15).toInt()
        limit?.let { l ->
            if (l.downKbps > 0) down = min(down, l.downKbps)
            if (l.upKbps > 0) up = min(up, l.upKbps)
        }

        val addDown = grow(d.mac, down / 8.0 / 1024.0 * DEMO_SPEEDUP)
        val addUp = grow(d.mac + "#up", up / 8.0 / 1024.0 * DEMO_SPEEDUP)

        var newQuota = q
        if (q != null) {
            newQuota = q.copy(usedMb = q.usedMb + addDown)
            if (!q.reached && newQuota.reached) {
                _events.tryEmit(
                    when (q.action) {
                        QuotaAction.BLOCK -> "${d.displayName} hit its quota and was blocked"
                        QuotaAction.THROTTLE -> "${d.displayName} hit its quota and was throttled"
                        QuotaAction.NOTIFY -> "${d.displayName} reached its data quota"
                    },
                )
            }
        }
        return d.copy(
            liveDownKbps = down,
            liveUpKbps = up,
            sessionDownMb = d.sessionDownMb + addDown,
            sessionUpMb = d.sessionUpMb + addUp,
            quota = newQuota,
            limit = limit,
        )
    }

    private fun mk(mac: String, host: String, ip: String, conn: ConnectionType, base: Int): NetDevice {
        val id = DeviceIdentifier.identify(mac, host)
        baseRate[mac] = base
        return NetDevice(
            mac = mac, hostname = host, vendor = id.vendor, model = id.model, kind = id.kind,
            ip = ip, connection = conn, randomizedMac = id.randomized,
            sessionDownMb = Random.nextLong(30, 900), sessionUpMb = Random.nextLong(5, 120),
            liveDownKbps = 0, liveUpKbps = 0,
        )
    }

    private fun seed(): List<NetDevice> = listOf(
        mk("3C:22:FB:4A:91:0C", "Ahmed-iPhone", "192.168.88.21", ConnectionType.WIFI_5, 5200),
        mk("8C:77:12:B3:5E:7A", "Galaxy-S23", "192.168.88.22", ConnectionType.WIFI_5, 3800)
            .copy(limit = SpeedLimit(downKbps = 2000, upKbps = 512)),
        mk("64:09:80:1D:C2:44", "Redmi-Note-12", "192.168.88.23", ConnectionType.WIFI_24, 1800)
            .copy(quota = DataQuota(capMb = 2048, usedMb = 1900, action = QuotaAction.THROTTLE)),
        mk("3C:A9:F4:77:0B:19", "DESKTOP-K4F2", "192.168.88.30", ConnectionType.LAN, 7400),
        mk("00:1C:62:A0:55:E1", "LivingRoom-TV", "192.168.88.40", ConnectionType.WIFI_5, 9800),
        mk("00:D9:D1:3F:6A:02", "PS5", "192.168.88.41", ConnectionType.LAN, 1500).copy(blocked = true),
        mk("24:6F:28:0E:9B:D3", "esp-plug-01", "192.168.88.60", ConnectionType.WIFI_24, 40),
        mk("DA:A1:19:5C:02:7E", "Android", "192.168.88.24", ConnectionType.WIFI_5, 2600),
    )

    private companion object {
        const val GUEST_MAC = "A2:1B:3C:55:90:11"
        const val DEMO_SPEEDUP = 10.0
    }
}
