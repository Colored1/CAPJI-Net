package com.capji.netpulse.remote

import android.content.Context
import com.capji.netpulse.data.net.Diag
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteState(
    val latestVersionCode: Long = 0,
    val updateMessage: String = "",
    val updateUrl: String = "",
    val forceUpdate: Boolean = false,
    val bannerEnabled: Boolean = false,
    val bannerText: String = "",
    val bannerLink: String = "",
    val maintenance: Boolean = false,
    val maintenanceMessage: String = "",
    /** false when google-services.json is missing: every remote feature stays off (safe defaults). */
    val firebaseReady: Boolean = false,
)

/**
 * Firebase Remote Config wrapper with safe defaults. Nothing here can crash the app:
 * without Firebase configuration it does nothing; every Firebase call is wrapped.
 */
object RemoteConfigManager {
    const val K_LATEST = "latest_version_code"
    const val K_UPDATE_MSG = "update_message"
    const val K_UPDATE_URL = "update_url"
    const val K_FORCE = "force_update"
    const val K_BANNER_ON = "announcement_banner_enabled"
    const val K_BANNER_TEXT = "announcement_text"
    const val K_BANNER_LINK = "announcement_link"
    const val K_MAINT = "maintenance_mode"
    const val K_MAINT_MSG = "maintenance_message"

    private val _state = MutableStateFlow(RemoteState())
    val state: StateFlow<RemoteState> = _state.asStateFlow()
    private var rc: FirebaseRemoteConfig? = null

    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                Diag.add("RemoteConfig: Firebase not configured (add app/google-services.json) - remote features are off")
                return
            }
            val r = FirebaseRemoteConfig.getInstance()
            rc = r
            r.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(900).build())
            r.setDefaultsAsync(
                mapOf(
                    K_LATEST to 0L, K_UPDATE_MSG to "", K_UPDATE_URL to "", K_FORCE to false,
                    K_BANNER_ON to false, K_BANNER_TEXT to "", K_BANNER_LINK to "",
                    K_MAINT to false, K_MAINT_MSG to "",
                ),
            ).addOnCompleteListener { publish(r); fetch() }
            // real-time updates: values change on screen without waiting for the next fetch
            r.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    r.activate().addOnCompleteListener { publish(r) }
                }
                override fun onError(error: FirebaseRemoteConfigException) {
                    Diag.add("RemoteConfig realtime error: ${error.code}")
                }
            })
        } catch (t: Throwable) {
            Diag.add("RemoteConfig init failed: ${t.message}")
        }
    }

    /** Fetch + activate now (respects Firebase's 15 min throttle). Safe to call often. */
    fun fetch() {
        val r = rc ?: return
        try {
            r.fetchAndActivate().addOnCompleteListener { publish(r) }
        } catch (t: Throwable) {
            Diag.add("RemoteConfig fetch failed: ${t.message}")
        }
    }

    private fun publish(r: FirebaseRemoteConfig) {
        try {
            _state.value = RemoteState(
                latestVersionCode = r.getLong(K_LATEST),
                updateMessage = r.getString(K_UPDATE_MSG),
                updateUrl = r.getString(K_UPDATE_URL),
                forceUpdate = r.getBoolean(K_FORCE),
                bannerEnabled = r.getBoolean(K_BANNER_ON),
                bannerText = r.getString(K_BANNER_TEXT),
                bannerLink = r.getString(K_BANNER_LINK),
                maintenance = r.getBoolean(K_MAINT),
                maintenanceMessage = r.getString(K_MAINT_MSG),
                firebaseReady = true,
            )
        } catch (t: Throwable) {
            Diag.add("RemoteConfig publish failed: ${t.message}")
        }
    }
}
