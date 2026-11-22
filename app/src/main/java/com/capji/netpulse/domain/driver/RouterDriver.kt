package com.capji.netpulse.domain.driver

import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.domain.model.RouterInfo
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.SpeedLimit
import kotlinx.coroutines.flow.Flow

/**
 * One implementation per router family. The UI and ViewModels only know this interface,
 * so adding OpenWrt / TP-Link / Huawei means adding one class and one line in AppGraph.
 */
interface RouterDriver {
    val capabilities: Set<Capability>

    /** Human-readable notifications from the driver (e.g. "quota reached"). */
    val events: Flow<UiText>

    suspend fun connect(profile: RouterProfile, password: String): Result<RouterInfo>

    /** Hot stream of the current client list. */
    fun observeDevices(): Flow<List<NetDevice>>

    suspend fun refresh()

    suspend fun setBlocked(mac: String, blocked: Boolean): Result<Unit>

    /** null removes the limit. */
    suspend fun setLimit(mac: String, limit: SpeedLimit?): Result<Unit>

    /** null removes the quota. */
    suspend fun setQuota(mac: String, quota: DataQuota?): Result<Unit>

    /** minutes = 0 resumes immediately. */
    suspend fun pause(mac: String, minutes: Int): Result<Unit>

    fun disconnect()
}
