package com.capji.netpulse.domain.model

import androidx.annotation.StringRes
import com.capji.netpulse.R

enum class ConnectionType(val label: String) { WIFI_24("2.4 GHz"), WIFI_5("5 GHz"), LAN("LAN") }

enum class DeviceKind { PHONE, TABLET, LAPTOP, TV, CONSOLE, IOT, UNKNOWN }

enum class DeviceStatus { ONLINE, BLOCKED, THROTTLED, QUOTA_CAPPED, PAUSED }

enum class QuotaAction(@StringRes val labelRes: Int) {
    BLOCK(R.string.quota_action_block),
    THROTTLE(R.string.quota_action_throttle),
    NOTIFY(R.string.quota_action_notify),
}

/** 0 means "unlimited" for that direction. */
data class SpeedLimit(val downKbps: Int, val upKbps: Int)

data class DataQuota(val capMb: Long, val usedMb: Long, val action: QuotaAction) {
    val fraction: Float get() = if (capMb <= 0) 0f else (usedMb.toFloat() / capMb).coerceIn(0f, 1f)
    val remainingMb: Long get() = (capMb - usedMb).coerceAtLeast(0)
    val reached: Boolean get() = capMb > 0 && usedMb >= capMb
}

data class NetDevice(
    val mac: String,
    val hostname: String,
    val vendor: String,
    val model: String?,
    val kind: DeviceKind,
    val ip: String,
    val connection: ConnectionType,
    val randomizedMac: Boolean,
    val sessionDownMb: Long,
    val sessionUpMb: Long,
    val liveDownKbps: Int,
    val liveUpKbps: Int,
    val blocked: Boolean = false,
    val limit: SpeedLimit? = null,
    val quota: DataQuota? = null,
    val pausedUntilMs: Long? = null,
) {
    val id: String get() = mac
    val displayName: String get() = model ?: hostname.ifBlank { mac }

    val status: DeviceStatus
        get() = when {
            blocked -> DeviceStatus.BLOCKED
            pausedUntilMs != null -> DeviceStatus.PAUSED
            quota?.reached == true -> DeviceStatus.QUOTA_CAPPED
            limit != null -> DeviceStatus.THROTTLED
            else -> DeviceStatus.ONLINE
        }
}

/** What a router driver can really enforce. The UI disables controls that are not supported. */
enum class Capability(@StringRes val labelRes: Int) {
    BLOCK(R.string.cap_block),
    SPEED_LIMIT(R.string.cap_speed_limit),
    QUOTA(R.string.cap_quota),
    PAUSE(R.string.cap_pause),
}

enum class RouterType(val shortName: String, @StringRes val titleRes: Int, val implemented: Boolean) {
    DEMO("Demo", R.string.rt_demo, true),
    MIKROTIK("MikroTik", R.string.rt_mikrotik, true),
    OPENWRT("OpenWrt", R.string.rt_openwrt, false),
    GENERIC("Generic", R.string.rt_generic, false),
}

data class RouterProfile(
    val id: String,
    val name: String,
    val type: RouterType,
    val host: String,
    val username: String,
)

data class RouterInfo(
    val model: String,
    val firmware: String,
    val gateway: String,
    val capabilities: Set<Capability>,
    val notes: List<UiText>,
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val profile: RouterProfile, val info: RouterInfo) : ConnectionState
    data class Failed(val message: UiText) : ConnectionState
}

// ---- Speed test ----

enum class SpeedPhase { IDLE, PING, DOWNLOAD, UPLOAD, DONE, ERROR }

data class SpeedState(
    val phase: SpeedPhase = SpeedPhase.IDLE,
    val liveMbps: Float = 0f,
    val pingMs: Float? = null,
    val jitterMs: Float? = null,
    val downMbps: Float? = null,
    val upMbps: Float? = null,
    val error: UiText? = null,
)

data class SpeedRecord(
    val timeMs: Long,
    val pingMs: Float,
    val jitterMs: Float,
    val downMbps: Float,
    val upMbps: Float,
)
