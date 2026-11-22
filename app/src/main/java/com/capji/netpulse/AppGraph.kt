package com.capji.netpulse

import android.app.Application
import android.content.Context
import com.capji.netpulse.core.GatewayDetector
import com.capji.netpulse.data.ProfileStore
import com.capji.netpulse.data.RouterRepository
import com.capji.netpulse.data.driver.MikroTikRestDriver
import com.capji.netpulse.data.driver.MockRouterDriver
import com.capji.netpulse.data.driver.UnsupportedDriver
import com.capji.netpulse.domain.model.RouterType
import com.capji.netpulse.domain.model.SpeedRecord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Tiny manual DI container. Swap for Hilt/Koin when the app grows. */
object AppGraph {
    private lateinit var appContext: Context
    lateinit var profiles: ProfileStore
        private set

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    /** The only place that knows which driver class belongs to which router type. */
    val repo = RouterRepository { type ->
        when (type) {
            RouterType.DEMO -> MockRouterDriver()
            RouterType.MIKROTIK -> MikroTikRestDriver()
            else -> UnsupportedDriver(type.title)
        }
    }

    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val speedHistory = MutableStateFlow<List<SpeedRecord>>(emptyList())

    fun init(context: Context) {
        appContext = context.applicationContext
        profiles = ProfileStore(appContext)
    }

    fun detectGateway(): String? = GatewayDetector.detect(appContext)

    fun toast(message: String) {
        toasts.tryEmit(message)
    }
}

class NetPulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
