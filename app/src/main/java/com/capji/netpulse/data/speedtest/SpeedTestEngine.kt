package com.capji.netpulse.data.speedtest

import com.capji.netpulse.domain.model.SpeedPhase
import com.capji.netpulse.domain.model.SpeedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

interface SpeedTestEngine {
    /** Emits progress; the last emission has phase DONE. */
    fun run(): Flow<SpeedState>
}

/** Mean absolute difference between consecutive RTT samples (RFC 3550 style jitter). */
internal fun jitterOf(rtts: List<Float>): Float =
    if (rtts.size < 2) 0f else rtts.zipWithNext { a, b -> abs(b - a) }.average().toFloat()

/**
 * Real test against Cloudflare's public speed endpoints (https://speed.cloudflare.com/__down, __up).
 * Ping: 8 tiny requests; Download: streamed for ~8 s; Upload: repeated 1 MB POSTs for ~6 s.
 * Measuring on a single connection under-reports on very fast links - a production version should
 * open 4-6 parallel streams.
 */
class RealSpeedTestEngine(private val client: OkHttpClient) : SpeedTestEngine {

    override fun run(): Flow<SpeedState> = flow {
        var s = SpeedState(phase = SpeedPhase.PING)
        emit(s)

        // ---- Ping + jitter
        val rtts = mutableListOf<Float>()
        repeat(8) {
            currentCoroutineContext().ensureActive()
            val t0 = System.nanoTime()
            client.newCall(Request.Builder().url("$BASE/__down?bytes=0").build()).execute().use { it.body?.bytes() }
            rtts += (System.nanoTime() - t0) / 1_000_000f
            s = s.copy(pingMs = rtts.min(), jitterMs = jitterOf(rtts))
            emit(s)
        }

        // ---- Download
        s = s.copy(phase = SpeedPhase.DOWNLOAD, liveMbps = 0f)
        emit(s)
        client.newCall(Request.Builder().url("$BASE/__down?bytes=100000000").build()).execute().use { resp ->
            val source = resp.body!!.source()
            val buf = ByteArray(64 * 1024)
            val start = System.nanoTime()
            var total = 0L
            var lastEmit = start
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = source.read(buf)
                if (n < 0) break
                total += n
                val now = System.nanoTime()
                if (now - lastEmit > 100_000_000L) {
                    s = s.copy(liveMbps = mbps(total, now - start))
                    emit(s)
                    lastEmit = now
                }
                if (now - start > 8_000_000_000L) break
            }
            s = s.copy(downMbps = mbps(total, System.nanoTime() - start))
        }

        // ---- Upload
        s = s.copy(phase = SpeedPhase.UPLOAD, liveMbps = 0f)
        emit(s)
        val payload = ByteArray(1_000_000).toRequestBody("application/octet-stream".toMediaType())
        val start = System.nanoTime()
        var sent = 0L
        while (System.nanoTime() - start < 6_000_000_000L) {
            currentCoroutineContext().ensureActive()
            client.newCall(Request.Builder().url("$BASE/__up").post(payload).build()).execute().use { it.body?.bytes() }
            sent += 1_000_000
            s = s.copy(liveMbps = mbps(sent, System.nanoTime() - start))
            emit(s)
        }
        emit(s.copy(phase = SpeedPhase.DONE, upMbps = mbps(sent, System.nanoTime() - start), liveMbps = 0f))
    }.flowOn(Dispatchers.IO)

    private fun mbps(bytes: Long, nanos: Long): Float = (bytes * 8.0 / (nanos / 1e9) / 1e6).toFloat()

    private companion object { const val BASE = "https://speed.cloudflare.com" }
}

/** Offline simulation with realistic ramp-up curves, used by the "Simulate" switch and for UI work. */
class SimulatedSpeedTestEngine : SpeedTestEngine {

    override fun run(): Flow<SpeedState> = flow {
        var s = SpeedState(phase = SpeedPhase.PING)
        emit(s)

        val rtts = mutableListOf<Float>()
        repeat(10) {
            delay(130)
            rtts += 18f + Random.nextFloat() * 12f
            s = s.copy(pingMs = rtts.min(), jitterMs = jitterOf(rtts))
            emit(s)
        }

        s = s.copy(phase = SpeedPhase.DOWNLOAD)
        s = ramp(s, target = 94f) { st, v -> st.copy(liveMbps = v) }
        s = s.copy(downMbps = s.liveMbps, liveMbps = 0f)
        emit(s)
        delay(350)

        s = s.copy(phase = SpeedPhase.UPLOAD)
        s = ramp(s, target = 38f) { st, v -> st.copy(liveMbps = v) }
        emit(s.copy(phase = SpeedPhase.DONE, upMbps = s.liveMbps, liveMbps = 0f))
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<SpeedState>.ramp(
        from: SpeedState,
        target: Float,
        update: (SpeedState, Float) -> SpeedState,
    ): SpeedState {
        var s = from
        emit(s)
        for (i in 1..40) {
            delay(100)
            val p = i / 40f
            val v = target * (1f - exp(-4f * p)) * (0.94f + Random.nextFloat() * 0.12f)
            s = update(s, v)
            emit(s)
        }
        return s
    }
}
