package com.capji.netpulse.data

import com.capji.netpulse.AppGraph
import com.capji.netpulse.R
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException
import com.capji.netpulse.domain.driver.RouterDriver
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.RouterType
import com.capji.netpulse.domain.model.SpeedLimit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Single source of truth for "which router am I talking to" + its live device list. */
@OptIn(ExperimentalCoroutinesApi::class)
class RouterRepository(private val driverFor: (RouterType) -> RouterDriver) {

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val driver = MutableStateFlow<RouterDriver?>(null)

    private val names = MutableStateFlow<Map<String, String>>(emptyMap())
    private var nameJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    val devices: Flow<List<NetDevice>> = kotlinx.coroutines.flow.combine(
        driver.flatMapLatest { it?.observeDevices() ?: flowOf(emptyList()) }, names,
    ) { list, n -> list.map { enrich(it, n) } }

    /** Fills in a missing/generic hostname from SSDP/mDNS and re-runs brand/model detection. */
    private fun enrich(d: NetDevice, n: Map<String, String>): NetDevice {
        val generic = d.hostname.isBlank() || d.hostname.equals("unknown", true) || d.hostname.equals("android", true)
        val extra = n[d.ip] ?: return d
        if (!generic) return d
        val id = com.capji.netpulse.domain.identify.DeviceIdentifier.identify(d.mac, extra)
        return d.copy(hostname = extra, model = id.model ?: d.model, kind = if (id.model != null) id.kind else d.kind)
    }

    private fun startNames() {
        nameJob?.cancel()
        nameJob = scope.launch {
            while (true) {
                runCatching { names.value = com.capji.netpulse.core.NameDiscovery.discover(AppGraph.context) }
                kotlinx.coroutines.delay(90_000)
            }
        }
    }
    val events: Flow<UiText> = driver.flatMapLatest { it?.events ?: emptyFlow() }

    suspend fun connect(profile: RouterProfile, password: String) {
        _connection.value = ConnectionState.Connecting
        val d = driverFor(profile.type)
        d.connect(profile, password)
            .onSuccess {
                driver.value = d
                startNames()
                _connection.value = ConnectionState.Connected(profile, it)
            }
            .onFailure { _connection.value = ConnectionState.Failed(
                    (it as? UiTextException)?.text
                        ?: it.message?.takeIf { m -> m.isNotBlank() }?.let { m -> UiText(raw = m) }
                        ?: UiText(R.string.err_connection_failed),
                ) }
    }

    fun disconnect() {
        driver.value?.disconnect()
        driver.value = null
        nameJob?.cancel(); names.value = emptyMap()
        _connection.value = ConnectionState.Disconnected
    }

    private fun notConnected() = Result.failure<Unit>(UiTextException(UiText(R.string.err_not_connected)))

    fun currentDriver(): RouterDriver? = driver.value

    private fun profile() = (_connection.value as? ConnectionState.Connected)?.profile

    suspend fun refresh() = driver.value?.refresh()
    suspend fun setBlocked(mac: String, blocked: Boolean): Result<Unit> {
        val r = driver.value?.setBlocked(mac, blocked) ?: return notConnected()
        if (r.isSuccess) profile()?.let { com.capji.netpulse.work.Bg.cancelTimer(it.id, mac) }
        return r
    }
    suspend fun setLimit(mac: String, limit: SpeedLimit?) = driver.value?.setLimit(mac, limit) ?: notConnected()
    suspend fun setQuota(mac: String, quota: DataQuota?) = driver.value?.setQuota(mac, quota) ?: notConnected()
    suspend fun pause(mac: String, minutes: Int): Result<Unit> {
        val r = driver.value?.pause(mac, minutes) ?: return notConnected()
        val p = profile()
        if (r.isSuccess && p != null) {
            if (minutes > 0 && AppGraph.settings.bgTimers.value) {
                val name = (devicesSnapshot()[mac])?.displayName ?: mac
                com.capji.netpulse.work.Bg.startTimer(p, mac, name, minutes)
            } else com.capji.netpulse.work.Bg.cancelTimer(p.id, mac)
        }
        return r
    }

    private fun devicesSnapshot(): Map<String, NetDevice> = lastDevices

    @Volatile private var lastDevices: Map<String, NetDevice> = emptyMap()
    fun remember(list: List<NetDevice>) { lastDevices = list.associateBy { it.mac } }
}
