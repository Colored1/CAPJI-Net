package com.capji.netpulse.data

import com.capji.netpulse.domain.driver.RouterDriver
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.RouterType
import com.capji.netpulse.domain.model.SpeedLimit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

    val devices: Flow<List<NetDevice>> = driver.flatMapLatest { it?.observeDevices() ?: flowOf(emptyList()) }
    val events: Flow<String> = driver.flatMapLatest { it?.events ?: emptyFlow() }

    suspend fun connect(profile: RouterProfile, password: String) {
        _connection.value = ConnectionState.Connecting
        val d = driverFor(profile.type)
        d.connect(profile, password)
            .onSuccess {
                driver.value = d
                _connection.value = ConnectionState.Connected(profile, it)
            }
            .onFailure { _connection.value = ConnectionState.Failed(it.message ?: "Connection failed") }
    }

    fun disconnect() {
        driver.value?.disconnect()
        driver.value = null
        _connection.value = ConnectionState.Disconnected
    }

    private fun notConnected() = Result.failure<Unit>(IllegalStateException("Not connected to a router"))

    suspend fun refresh() = driver.value?.refresh()
    suspend fun setBlocked(mac: String, blocked: Boolean) = driver.value?.setBlocked(mac, blocked) ?: notConnected()
    suspend fun setLimit(mac: String, limit: SpeedLimit?) = driver.value?.setLimit(mac, limit) ?: notConnected()
    suspend fun setQuota(mac: String, quota: DataQuota?) = driver.value?.setQuota(mac, quota) ?: notConnected()
    suspend fun pause(mac: String, minutes: Int) = driver.value?.pause(mac, minutes) ?: notConnected()
}
