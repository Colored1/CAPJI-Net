package com.capji.netpulse.ui.viewmodel

import com.capji.netpulse.R
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.toUiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capji.netpulse.AppGraph
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.domain.model.SpeedLimit
import com.capji.netpulse.ui.util.formatMb
import com.capji.netpulse.ui.util.rateArg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesUi(
    val devices: List<NetDevice> = emptyList(),
    val selectedId: String? = null,
    val scanning: Boolean = false,
    val capabilities: Set<Capability> = emptySet(),
    /** Last ~40 samples of total download (Mbps) for the dashboard sparkline. */
    val throughput: List<Float> = emptyList(),
)

class DevicesViewModel : ViewModel() {
    private val repo = AppGraph.repo

    private val selected = MutableStateFlow<String?>(null)
    private val scanning = MutableStateFlow(false)
    private val throughput = MutableStateFlow<List<Float>>(emptyList())
    private val capabilities = repo.connection.map { (it as? ConnectionState.Connected)?.info?.capabilities ?: emptySet() }

    private val meta = combine(scanning, throughput, capabilities) { s, t, c -> Triple(s, t, c) }

    val state: StateFlow<DevicesUi> = combine(repo.devices, selected, meta) { devices, sel, (scan, tp, caps) ->
        DevicesUi(devices.sortedBy { it.displayName.lowercase() }, sel, scan, caps, tp)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DevicesUi())

    init {
        repo.devices.onEach { list ->
            repo.remember(list)
            val total = list.sumOf { it.liveDownKbps } / 1000f
            throughput.update { (it + total).takeLast(40) }
        }.launchIn(viewModelScope)
        repo.events.onEach { AppGraph.toast(it) }.launchIn(viewModelScope)
    }

    fun select(id: String?) {
        selected.value = id
    }

    fun rescan() {
        if (scanning.value) return
        viewModelScope.launch {
            scanning.value = true
            repo.refresh()
            scanning.value = false
        }
    }

    fun setBlocked(d: NetDevice, blocked: Boolean) = act(
        UiText(if (blocked) R.string.toast_blocked else R.string.toast_unblocked, listOf(d.displayName)),
    ) { repo.setBlocked(d.mac, blocked) }

    fun setLimit(d: NetDevice, limit: SpeedLimit?) = act(
        if (limit == null) UiText(R.string.toast_limit_removed, listOf(d.displayName))
        else UiText(R.string.toast_limit_applied, listOf(rateArg(limit.downKbps), rateArg(limit.upKbps))),
    ) { repo.setLimit(d.mac, limit) }

    fun setQuota(d: NetDevice, quota: DataQuota?) = act(
        if (quota == null) UiText(R.string.toast_quota_removed, listOf(d.displayName))
        else UiText(R.string.toast_quota_set, listOf(formatMb(quota.capMb), UiText(quota.action.labelRes))),
    ) { repo.setQuota(d.mac, quota) }

    fun pause(d: NetDevice, minutes: Int) = act(
        if (minutes <= 0) UiText(R.string.toast_resumed, listOf(d.displayName))
        else UiText(R.string.toast_paused, listOf(d.displayName, minutes)),
    ) { repo.pause(d.mac, minutes) }

    private fun act(okMessage: UiText, block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block()
                .onSuccess { AppGraph.toast(okMessage) }
                .onFailure { AppGraph.toast(it.toUiText()) }
        }
    }
}
