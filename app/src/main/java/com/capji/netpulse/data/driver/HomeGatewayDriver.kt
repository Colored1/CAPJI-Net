package com.capji.netpulse.data.driver

import com.capji.netpulse.R
import com.capji.netpulse.data.net.Diag
import com.capji.netpulse.data.net.HttpSession
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
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/** One client as the router reports it. */
data class RawClient(
    val mac: String,
    val ip: String,
    val hostname: String,
    val connection: ConnectionType = ConnectionType.WIFI_24,
    val rxBytes: Long? = null,
    val txBytes: Long? = null,
)

data class GatewayId(val model: String, val firmware: String = "")

/** Thrown by a driver when the router answered with its login page (session expired / kicked out). */
class SessionExpired : Exception("session expired")

/**
 * Shared engine for web-UI home gateways (Huawei, ZTE, TP-Link...). A concrete driver only implements the
 * blocking router calls ([login], [readClients], [readBlocked], [writeBlock]); this class adds:
 *  - polling + re-login when the session expires
 *  - blocked devices that went offline stay in the list (so they can be unblocked)
 *  - timed pause emulated by the app (block now, unblock automatically)
 *  - verification of block rules (read back from the router; reports when the router ignored the rule)
 *  - app-level quota enforcement when the router exposes per-client byte counters
 */
abstract class HomeGatewayDriver : RouterDriver {
    protected val http = HttpSession()
    protected lateinit var host: String
    protected var user = ""
    protected var pass = ""

    protected abstract val brand: String
    protected abstract val baseCaps: Set<Capability>
    protected open val hasCounters: Boolean = false
    protected open val driverNotes: List<UiText> = emptyList()

    protected abstract fun login(): GatewayId
    protected abstract fun readClients(): List<RawClient>

    /** MACs currently blocked on the router, or null when this driver can't read them back. */
    protected open fun readBlocked(): Set<String>? = null

    protected open fun writeBlock(mac: String, blocked: Boolean, known: RawClient?) {
        throw UiTextException(UiText(R.string.err_block_unsupported))
    }

    private var caps: Set<Capability> = emptySet()
    override val capabilities: Set<Capability> get() = caps

    private val _events = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    override val events: Flow<UiText> get() = _events

    private val state = MutableStateFlow<List<NetDevice>>(emptyList())
    private val lock = Any()
    private var scope: CoroutineScope? = null

    private val blocked = ConcurrentHashMap.newKeySet<String>()
    private val snapshot = ConcurrentHashMap<String, NetDevice>()
    private val pauses = ConcurrentHashMap<String, Long>()
    private val pauseJobs = ConcurrentHashMap<String, Job>()
    private val quotas = ConcurrentHashMap<String, QuotaState>()
    private val lastBytes = ConcurrentHashMap<String, Triple<Long, Long, Long>>()
    private var lastPollOk = true

    private class QuotaState(val capMb: Long, val action: QuotaAction, var baselineBytes: Long?, var notified: Boolean = false)

    // ---------------------------------------------------------------- connect

    override suspend fun connect(profile: RouterProfile, password: String): Result<RouterInfo> =
        withContext(Dispatchers.IO) {
            try {
                host = profile.host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
                user = profile.username
                pass = password
                http.reset()
                val id = synchronized(lock) { login() }
                var c = baseCaps
                if (Capability.BLOCK in c) c = c + Capability.PAUSE
                if (hasCounters) c = c + Capability.QUOTA
                caps = c
                startLoop()
                val notes = buildList {
                    addAll(driverNotes)
                    if (Capability.PAUSE in c) add(UiText(R.string.note_pause_app))
                    if (Capability.SPEED_LIMIT !in c) add(UiText(R.string.note_no_limit))
                    if (Capability.QUOTA !in c) add(UiText(R.string.note_no_quota))
                }
                Result.success(RouterInfo(id.model, id.firmware, host, c, notes))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Diag.add("connect failed: ${e::class.simpleName}: ${e.message}")
                Result.failure(mapError(e))
            }
        }

    protected fun mapError(e: Throwable): Throwable = when (e) {
        is UiTextException -> e
        is UnknownHostException, is ConnectException, is SocketTimeoutException, is NoRouteToHostException ->
            UiTextException(UiText(R.string.err_unreachable, listOf(host)))
        is IOException -> UiTextException(UiText(R.string.err_unreachable, listOf(host)))
        else -> UiTextException(UiText(raw = e.message ?: e::class.simpleName ?: "error"))
    }

    // ---------------------------------------------------------------- polling

    private fun startLoop() {
        scope?.cancel()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            while (isActive) {
                pollOnce()
                delay(POLL_MS)
            }
        }
    }

    /** Runs [block]; if the router says the session is gone, logs in again once and retries. */
    private fun <T> authed(block: () -> T): T = synchronized(lock) {
        try {
            block()
        } catch (e: SessionExpired) {
            Diag.add("session expired -> re-login")
            http.reset()
            login()
            block()
        }
    }

    private fun pollOnce() {
        try {
            val raw = authed { readClients() }
            runCatching { authed { readBlocked() } }.getOrNull()?.let { b -> blocked.clear(); blocked.addAll(b) }
            state.value = build(raw)
            if (!lastPollOk) lastPollOk = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Diag.add("poll failed: ${e::class.simpleName}: ${e.message}")
            if (lastPollOk) {
                lastPollOk = false
                _events.tryEmit(UiText(R.string.evt_router_lost))
            }
        }
    }

    private fun build(raw: List<RawClient>): List<NetDevice> {
        val now = System.currentTimeMillis()
        val seen = HashSet<String>()
        val out = ArrayList<NetDevice>()
        for (c in raw) {
            seen += c.mac
            val ident = DeviceIdentifier.identify(c.mac, c.hostname)
            val speedDown: Int
            val speedUp: Int
            val rx = c.rxBytes
            val tx = c.txBytes
            if (rx != null && tx != null) {
                val prev = lastBytes[c.mac]
                if (prev != null && now > prev.third) {
                    val dt = (now - prev.third) / 1000.0
                    speedDown = (((rx - prev.first).coerceAtLeast(0) * 8 / 1000.0) / dt).toInt()
                    speedUp = (((tx - prev.second).coerceAtLeast(0) * 8 / 1000.0) / dt).toInt()
                } else { speedDown = 0; speedUp = 0 }
                lastBytes[c.mac] = Triple(rx, tx, now)
            } else { speedDown = 0; speedUp = 0 }

            val q = quotas[c.mac]
            var quota: DataQuota? = null
            if (q != null && rx != null && tx != null) {
                if (q.baselineBytes == null) q.baselineBytes = rx + tx
                val usedMb = ((rx + tx) - (q.baselineBytes ?: 0L)).coerceAtLeast(0) / 1_000_000
                quota = DataQuota(q.capMb, usedMb, q.action)
                if (quota.reached && !q.notified) {
                    q.notified = true
                    enforceQuota(c, q)
                }
            }
            val deadline = pauses[c.mac]?.takeIf { it > now }
            val isBlocked = c.mac in blocked && deadline == null
            val dev = NetDevice(
                mac = c.mac, hostname = c.hostname, vendor = ident.vendor, model = ident.model, kind = ident.kind,
                ip = c.ip, connection = c.connection, randomizedMac = ident.randomized,
                sessionDownMb = (rx ?: 0L) / 1_000_000, sessionUpMb = (tx ?: 0L) / 1_000_000,
                liveDownKbps = speedDown, liveUpKbps = speedUp,
                blocked = isBlocked, quota = quota, pausedUntilMs = deadline,
            )
            snapshot[c.mac] = dev
            out += dev
        }
        // a blocked device may drop off the router's client list - keep it so the user can unblock it
        for (m in blocked) {
            if (m in seen) continue
            val old = snapshot[m] ?: continue
            val deadline = pauses[m]?.takeIf { it > now }
            out += old.copy(blocked = deadline == null, pausedUntilMs = deadline, liveDownKbps = 0, liveUpKbps = 0)
        }
        return out
    }

    private fun enforceQuota(c: RawClient, q: QuotaState) {
        val name = c.hostname.ifBlank { c.mac }
        when (q.action) {
            QuotaAction.BLOCK -> {
                runCatching { authed { writeBlock(c.mac, true, c) } }
                    .onSuccess { blocked += c.mac; _events.tryEmit(UiText(R.string.evt_quota_block, listOf(name))) }
                    .onFailure { _events.tryEmit(UiText(R.string.evt_quota_notify, listOf(name))) }
            }
            else -> _events.tryEmit(UiText(R.string.evt_quota_notify, listOf(name)))
        }
    }

    override fun observeDevices(): Flow<List<NetDevice>> = state

    override suspend fun refresh() {
        withContext(Dispatchers.IO) { pollOnce() }
    }

    // ---------------------------------------------------------------- actions

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Diag.add("action failed: ${e::class.simpleName}: ${e.message}")
            Result.failure(mapError(e))
        }
    }

    private fun applyBlock(mac: String, on: Boolean) {
        val known = snapshot[mac]?.let { RawClient(it.mac, it.ip, it.hostname, it.connection) }
        authed { writeBlock(mac, on, known) }
        val after = runCatching { authed { readBlocked() } }.getOrNull()
        if (after != null) {
            if ((mac in after) != on) throw UiTextException(UiText(R.string.err_rule_rejected))
            blocked.clear(); blocked.addAll(after)
        } else {
            if (on) blocked += mac else blocked -= mac
        }
    }

    override suspend fun setBlocked(mac: String, blocked: Boolean): Result<Unit> = io {
        cancelPause(mac)
        applyBlock(mac, blocked)
        pollOnce()
    }

    override suspend fun pause(mac: String, minutes: Int): Result<Unit> = io {
        if (minutes <= 0) {
            cancelPause(mac)
            applyBlock(mac, false)
        } else {
            applyBlock(mac, true)
            val deadline = System.currentTimeMillis() + minutes * 60_000L
            cancelPause(mac)
            pauses[mac] = deadline
            pauseJobs[mac] = scope!!.launch {
                delay(minutes * 60_000L)
                pauses.remove(mac)
                val name = snapshot[mac]?.displayName ?: mac
                runCatching { withContext(Dispatchers.IO) { applyBlock(mac, false); pollOnce() } }
                    .onSuccess { _events.tryEmit(UiText(R.string.evt_auto_unblocked, listOf(name))) }
            }
        }
        pollOnce()
    }

    private fun cancelPause(mac: String) {
        pauses.remove(mac)
        pauseJobs.remove(mac)?.cancel()
    }

    override suspend fun setLimit(mac: String, limit: SpeedLimit?): Result<Unit> =
        Result.failure(UiTextException(UiText(R.string.limit_unsupported)))

    override suspend fun setQuota(mac: String, quota: DataQuota?): Result<Unit> {
        if (Capability.QUOTA !in caps) return Result.failure(UiTextException(UiText(R.string.quota_unsupported)))
        if (quota == null) quotas.remove(mac) else quotas[mac] = QuotaState(quota.capMb, quota.action, null)
        return Result.success(Unit)
    }

    override fun disconnect() {
        scope?.cancel()
        scope = null
        pauseJobs.values.forEach { it.cancel() }
        pauseJobs.clear(); pauses.clear(); quotas.clear(); snapshot.clear(); blocked.clear(); lastBytes.clear()
        state.value = emptyList()
        http.reset()
    }

    protected fun url(path: String) = "http://$host$path"

    private companion object { const val POLL_MS = 4000L }
}
