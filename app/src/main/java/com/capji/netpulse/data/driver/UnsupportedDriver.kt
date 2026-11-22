package com.capji.netpulse.data.driver

import com.capji.netpulse.domain.driver.RouterDriver
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.domain.model.RouterInfo
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.SpeedLimit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** Placeholder for router families that have no driver yet (OpenWrt, generic web UI...). */
class UnsupportedDriver(private val title: String) : RouterDriver {
    override val capabilities = emptySet<Capability>()
    override val events: Flow<String> = emptyFlow()

    private fun no() = Result.failure<Unit>(UnsupportedOperationException("$title driver isn't implemented yet"))

    override suspend fun connect(profile: RouterProfile, password: String): Result<RouterInfo> =
        Result.failure(UnsupportedOperationException("$title driver isn't implemented yet - see docs/ENGINEERING_NOTES.md"))

    override fun observeDevices(): Flow<List<NetDevice>> = flowOf(emptyList())
    override suspend fun refresh() = Unit
    override suspend fun setBlocked(mac: String, blocked: Boolean) = no()
    override suspend fun setLimit(mac: String, limit: SpeedLimit?) = no()
    override suspend fun setQuota(mac: String, quota: DataQuota?) = no()
    override suspend fun pause(mac: String, minutes: Int) = no()
    override fun disconnect() = Unit
}
