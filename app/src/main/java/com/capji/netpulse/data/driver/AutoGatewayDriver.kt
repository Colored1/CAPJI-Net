package com.capji.netpulse.data.driver

import com.capji.netpulse.AppGraph
import com.capji.netpulse.R
import com.capji.netpulse.data.net.Diag
import com.capji.netpulse.data.net.HttpSession
import com.capji.netpulse.domain.driver.RouterDriver
import com.capji.netpulse.domain.model.Capability
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.NetDevice
import com.capji.netpulse.domain.model.RouterInfo
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.RouterType
import com.capji.netpulse.domain.model.SpeedLimit
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.domain.model.UiTextException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

/**
 * "Auto" mode: finds the router (typed address, detected gateway, then the usual 192.168.x.1 addresses),
 * recognises the brand with harmless GET probes, then logs in ONCE with the matching driver.
 * If the brand is unknown it falls back to [ScanOnlyDriver] instead of failing.
 */
class AutoGatewayDriver : RouterDriver {
    private var delegate: RouterDriver? = null

    override val capabilities: Set<Capability> get() = delegate?.capabilities ?: emptySet()
    override val events: Flow<UiText> get() = delegate?.events ?: emptyFlow()

    override suspend fun connect(profile: RouterProfile, password: String): Result<RouterInfo> =
        withContext(Dispatchers.IO) {
            val candidates = listOf(profile.host.trim(), AppGraph.detectGateway().orEmpty()) + DEFAULT_HOSTS
            var firstError: Throwable? = null
            for (h in candidates.filter { it.isNotBlank() }.distinct()) {
                val type = probe(h) ?: continue
                Diag.add("auto: $h looks like $type")
                val d = AppGraph.driverFor(type)
                val r = d.connect(profile.copy(host = h, type = type), password)
                if (r.isSuccess) { delegate = d; return@withContext r }
                if (firstError == null) firstError = r.exceptionOrNull()
                d.disconnect()
                // wrong password / locked: stop here so we don't trigger a lock-out on other hosts
                if ((firstError as? UiTextException)?.text?.id in setOf(R.string.err_wrong_credentials, R.string.err_login_locked)) {
                    return@withContext Result.failure(firstError!!)
                }
            }
            val scan = ScanOnlyDriver(firstError?.let { (it as? UiTextException)?.text } ?: UiText(R.string.note_auto_unknown))
            val r = scan.connect(profile, password)
            if (r.isSuccess) delegate = scan
            r
        }

    /** Recognises the router brand without logging in. */
    private fun probe(host: String): RouterType? {
        val http = HttpSession()
        val root = runCatching { http.get("http://$host/") }.getOrNull() ?: return null
        val page = (root.body + " " + root.headers.toString()).lowercase()
        runCatching {
            if (http.get("http://$host/api/webserver/SesTokInfo").body.contains("<SesInfo>", true)) return RouterType.HUAWEI_LTE
        }
        runCatching {
            val h = http.get("http://$host/HNAP1/")
            if (h.body.contains("HNAP", true) && h.code == 200) return RouterType.DLINK
        }
        if (page.contains("tenda") || page.contains("/login/auth")) return RouterType.TENDA
        if (page.contains("frm_logintoken") || page.contains("zxhn") || page.contains("zte corporation")) return RouterType.ZTE
        if (page.contains("tp-link") || page.contains("tplink") || page.contains("/userrpm/") || page.contains("luci") && page.contains("tp")) return RouterType.TPLINK
        if (root.code == 401 && root.headers.toString().lowercase().contains("tp-link")) return RouterType.TPLINK
        val rand = runCatching { http.postForm("http://$host/asp/GetRandCount.asp", emptyMap()) }.getOrNull()
        if (rand != null && rand.code == 200 && rand.body.trim().removePrefix("﻿").let { it.isNotEmpty() && it.length < 80 && !it.contains("<") }) return RouterType.HUAWEI_ONT
        if (page.contains("huawei") || page.contains("hg8") || page.contains("echolife")) return RouterType.HUAWEI_ONT
        if (page.contains("routeros") || page.contains("mikrotik")) return RouterType.MIKROTIK
        return null
    }

    override fun observeDevices(): Flow<List<NetDevice>> = delegate?.observeDevices() ?: emptyFlow()
    override suspend fun refresh() { delegate?.refresh() }
    private fun gone() = Result.failure<Unit>(UiTextException(UiText(R.string.err_not_connected)))
    override suspend fun setBlocked(mac: String, blocked: Boolean) = delegate?.setBlocked(mac, blocked) ?: gone()
    override suspend fun setLimit(mac: String, limit: SpeedLimit?) = delegate?.setLimit(mac, limit) ?: gone()
    override suspend fun setQuota(mac: String, quota: DataQuota?) = delegate?.setQuota(mac, quota) ?: gone()
    override suspend fun pause(mac: String, minutes: Int) = delegate?.pause(mac, minutes) ?: gone()
    override fun disconnect() { delegate?.disconnect(); delegate = null }

    private companion object {
        val DEFAULT_HOSTS = listOf("192.168.1.1", "192.168.0.1", "192.168.8.1", "192.168.100.1", "192.168.10.1", "192.168.2.1", "10.0.0.1", "192.168.88.1")
    }
}
