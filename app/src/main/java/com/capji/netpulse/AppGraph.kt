package com.capji.netpulse

import android.app.Application
import android.content.Context
import com.capji.netpulse.core.GatewayDetector
import com.capji.netpulse.data.ProfileStore
import com.capji.netpulse.data.RouterRepository
import com.capji.netpulse.data.SettingsStore
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.UiText
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.capji.netpulse.data.driver.MikroTikRestDriver
import com.capji.netpulse.data.driver.MockRouterDriver
import com.capji.netpulse.data.driver.AutoGatewayDriver
import com.capji.netpulse.data.driver.HuaweiHiLinkDriver
import com.capji.netpulse.data.driver.HuaweiOntDriver
import com.capji.netpulse.data.driver.ScanOnlyDriver
import com.capji.netpulse.data.driver.TpLinkDriver
import com.capji.netpulse.data.driver.ZteDriver
import com.capji.netpulse.domain.driver.RouterDriver
import com.capji.netpulse.domain.model.RouterType
import com.capji.netpulse.domain.model.SpeedRecord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Tiny manual DI container. Swap for Hilt/Koin when the app grows. */
object AppGraph {
    private lateinit var appContext: Context
    val context: Context get() = appContext
    val hotspotOpen = MutableStateFlow(false)
    lateinit var profiles: ProfileStore
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    /** The only place that knows which driver class belongs to which router type. */
    fun driverFor(type: RouterType): RouterDriver = when (type) {
        RouterType.DEMO -> MockRouterDriver()
        RouterType.MIKROTIK -> MikroTikRestDriver()
        RouterType.HUAWEI_LTE -> HuaweiHiLinkDriver()
        RouterType.HUAWEI_ONT -> HuaweiOntDriver()
        RouterType.ZTE -> ZteDriver()
        RouterType.TPLINK -> TpLinkDriver()
        RouterType.GENERIC -> ScanOnlyDriver()
        RouterType.AUTO -> AutoGatewayDriver()
    }

    val repo = RouterRepository { type -> driverFor(type) }

    val toasts = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    lateinit var settings: SettingsStore
        private set
    val profilesState = MutableStateFlow<List<RouterProfile>>(emptyList())
    val speedHistory = MutableStateFlow<List<SpeedRecord>>(emptyList())

    fun init(context: Context) {
        appContext = context.applicationContext
        profiles = ProfileStore(appContext)
        settings = SettingsStore(appContext)
        profilesState.value = profiles.load()
    }

    fun clearProfiles() {
        profilesState.value = profiles.clear()
    }

    fun detectGateway(): String? = GatewayDetector.detect(appContext)

    fun toast(message: UiText) {
        toasts.tryEmit(message)
    }
}

class NetPulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // First launch: Arabic by default. After that the user's choice is kept by AppCompat.
        val prefs = getSharedPreferences("boot", MODE_PRIVATE)
        if (!prefs.getBoolean("lang_init", false)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
            prefs.edit().putBoolean("lang_init", true).apply()
        }
    }
}
