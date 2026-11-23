package com.capji.netpulse.data.driver

import com.capji.netpulse.AppGraph
import com.capji.netpulse.R
import com.capji.netpulse.core.LanScanner
import com.capji.netpulse.domain.driver.RouterDriver
import com.capji.netpulse.domain.identify.DeviceIdentifier
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionType
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.domain.model.RouterInfo
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.SpeedLimit
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Works with ANY router (D-Link, Tenda, Mercusys, ISP boxes...): no login, devices are discovered by scanning
 * the Wi-Fi network. It can show and identify devices but cannot block them - that needs the router's admin login.
 */
class ScanOnlyDriver(private val reason: UiText? = null) : RouterDriver {
    override val capabilities = emptySet<Capability>()
    override val events: Flow<UiText> = emptyFlow()

    private val state = MutableStateFlow<List<NetDevice>>(emptyList())
    private var scope: CoroutineScope? = null
    private var gateway = ""

    override suspend fun connect(profile: RouterProfile, password: String): Result<RouterInfo> {
        val subnet = LanScanner.wifiSubnet(AppGraph.context)
            ?: return Result.failure(UiTextException(UiText(R.string.err_no_wifi)))
        gateway = profile.host.ifBlank { subnet.base + "1" }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = s
        s.launch {
            while (isActive) {
                scanOnce()
                delay(20_000)
            }
        }
        val notes = buildList {
            reason?.let { add(it) }
            add(UiText(R.string.note_scan_only))
            add(UiText(R.string.note_no_mac))
        }
        return Result.success(RouterInfo("Wi-Fi scan", "-", gateway, emptySet(), notes))
    }

    private suspend fun scanOnce() {
        val subnet = LanScanner.wifiSubnet(AppGraph.context) ?: return
        try {
            val found = LanScanner.scan(subnet)
            state.value = found.map { f ->
                val mac = f.mac ?: f.ip
                val ident = DeviceIdentifier.identify(mac, f.host.orEmpty())
                NetDevice(
                    mac = mac, hostname = f.host.orEmpty(), vendor = ident.vendor, model = ident.model, kind = ident.kind,
                    ip = f.ip, connection = ConnectionType.WIFI_24, randomizedMac = f.mac != null && ident.randomized,
                    sessionDownMb = 0, sessionUpMb = 0, liveDownKbps = 0, liveUpKbps = 0,
                )
            }.sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) { }
    }

    override fun observeDevices(): Flow<List<NetDevice>> = state
    override suspend fun refresh() = scanOnce()

    private fun no() = Result.failure<Unit>(UiTextException(UiText(R.string.err_no_control)))
    override suspend fun setBlocked(mac: String, blocked: Boolean) = no()
    override suspend fun setLimit(mac: String, limit: SpeedLimit?) = no()
    override suspend fun setQuota(mac: String, quota: DataQuota?) = no()
    override suspend fun pause(mac: String, minutes: Int) = no()

    override fun disconnect() {
        scope?.cancel()
        scope = null
        state.value = emptyList()
    }
}
