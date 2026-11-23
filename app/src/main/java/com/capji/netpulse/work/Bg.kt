package com.capji.netpulse.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.capji.netpulse.AppGraph
import com.capji.netpulse.R
import com.capji.netpulse.data.net.Diag
import com.capji.netpulse.domain.driver.RouterDriver
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.RouterType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class Schedule(
    val id: String, val profileId: String, val mac: String, val name: String,
    val startMin: Int, val endMin: Int, val active: Boolean = false,
) {
    fun inWindow(nowMin: Int): Boolean =
        if (startMin <= endMin) nowMin in startMin until endMin else nowMin >= startMin || nowMin < endMin
}

data class TimerEntry(val profileId: String, val mac: String, val name: String, val atMs: Long)

/**
 * Background engine: timed pauses and daily block schedules survive closing the app (WorkManager is persisted
 * by Android and restarts after reboot). Android may delay work by a few minutes in battery saver / Doze.
 */
object Bg {
    private const val CH = "capji_bg"
    private val prefs get() = AppGraph.context.getSharedPreferences("bg", Context.MODE_PRIVATE)
    private fun wm() = WorkManager.getInstance(AppGraph.context)

    // ------------------------------------------------------------- storage
    fun timers(): List<TimerEntry> = runCatching {
        val a = JSONArray(prefs.getString("timers", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> TimerEntry(o.getString("p"), o.getString("m"), o.optString("n"), o.getLong("t")) } }
    }.getOrDefault(emptyList())

    private fun saveTimers(l: List<TimerEntry>) {
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("p", it.profileId).put("m", it.mac).put("n", it.name).put("t", it.atMs)) }
        prefs.edit().putString("timers", a.toString()).apply()
    }

    fun schedules(): List<Schedule> = runCatching {
        val a = JSONArray(prefs.getString("schedules", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Schedule(o.getString("id"), o.getString("p"), o.getString("m"), o.optString("n"), o.getInt("s"), o.getInt("e"), o.optBoolean("a"))
        }
    }.getOrDefault(emptyList())

    private fun saveSchedules(l: List<Schedule>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("p", it.profileId).put("m", it.mac).put("n", it.name).put("s", it.startMin).put("e", it.endMin).put("a", it.active)) }
        prefs.edit().putString("schedules", a.toString()).apply()
    }

    fun scheduleFor(profileId: String, mac: String) = schedules().firstOrNull { it.profileId == profileId && it.mac == mac }

    // ------------------------------------------------------------- timers
    fun startTimer(profile: RouterProfile, mac: String, name: String, minutes: Int) {
        if (profile.type == RouterType.DEMO || profile.type == RouterType.MIKROTIK) return // router removes it itself / simulated
        cancelTimer(profile.id, mac)
        val at = System.currentTimeMillis() + minutes * 60_000L
        saveTimers(timers() + TimerEntry(profile.id, mac, name, at))
        wm().enqueueUniqueWork(
            "timer-${profile.id}-$mac", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<TimerWorker>()
                .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .setInputData(workDataOf("p" to profile.id, "m" to mac, "n" to name))
                .build(),
        )
    }

    fun cancelTimer(profileId: String, mac: String) {
        saveTimers(timers().filterNot { it.profileId == profileId && it.mac == mac })
        wm().cancelUniqueWork("timer-$profileId-$mac")
    }

    // ------------------------------------------------------------- schedules
    fun setSchedule(profile: RouterProfile, mac: String, name: String, startMin: Int, endMin: Int) {
        val rest = schedules().filterNot { it.profileId == profile.id && it.mac == mac }
        saveSchedules(rest + Schedule("${profile.id}|$mac", profile.id, mac, name, startMin, endMin))
        kick()
    }

    fun removeSchedule(profileId: String, mac: String) {
        val s = scheduleFor(profileId, mac) ?: return
        saveSchedules(schedules().filterNot { it.id == s.id }.also { })
        if (s.active) wm().enqueue(OneTimeWorkRequestBuilder<ReleaseWorker>().setInputData(workDataOf("p" to profileId, "m" to mac, "n" to s.name)).build())
        kick()
    }

    /** Runs the schedule check now and keeps a 15-minute safety net alive. */
    fun kick() {
        wm().enqueueUniqueWork("schedules-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ScheduleWorker>().build())
        wm().enqueueUniquePeriodicWork("schedules-safety", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ScheduleWorker>(15, TimeUnit.MINUTES).build())
    }

    fun nextBoundaryDelayMin(list: List<Schedule>): Long {
        if (list.isEmpty()) return 0
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        var best = 24 * 60
        for (s in list) for (b in intArrayOf(s.startMin, s.endMin)) {
            val d = ((b - now) + 24 * 60) % (24 * 60)
            if (d in 1 until best) best = d
        }
        return best.toLong()
    }

    fun clearAll() {
        wm().cancelAllWork()
        prefs.edit().clear().apply()
    }

    // ------------------------------------------------------------- running against the router
    suspend fun <T> withRouter(profileId: String, block: suspend (RouterDriver) -> T): Result<T> {
        val live = AppGraph.repo.connection.value as? ConnectionState.Connected
        val d = AppGraph.repo.currentDriver()
        if (live != null && live.profile.id == profileId && d != null) return runCatching { block(d) }
        val profile = AppGraph.profiles.load().firstOrNull { it.id == profileId }
            ?: return Result.failure(IllegalStateException("profile gone"))
        val pass = AppGraph.secrets.get(profileId) ?: return Result.failure(IllegalStateException("no stored password"))
        val driver = AppGraph.driverFor(profile.type)
        val c = driver.connect(profile, pass)
        if (c.isFailure) return Result.failure(c.exceptionOrNull() ?: IllegalStateException("connect"))
        return try { runCatching { block(driver) } } finally { driver.disconnect() }
    }

    fun notify(ctx: Context, text: String) {
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, ctx.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT))
            nm.notify(text.hashCode(), NotificationCompat.Builder(ctx, CH).setSmallIcon(R.mipmap.ic_launcher).setContentTitle(ctx.getString(R.string.app_name)).setContentText(text).build())
        }
    }
}

class TimerWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val pid = inputData.getString("p") ?: return Result.failure()
        val mac = inputData.getString("m") ?: return Result.failure()
        val name = inputData.getString("n").orEmpty().ifBlank { mac }
        val r = Bg.withRouter(pid) { it.setBlocked(mac, false).getOrThrow() }
        return if (r.isSuccess) {
            Bg.cancelTimer(pid, mac)
            Bg.notify(applicationContext, applicationContext.getString(R.string.evt_auto_unblocked, name))
            Diag.add("bg timer: unblocked $mac")
            Result.success()
        } else {
            Diag.add("bg timer failed: ${r.exceptionOrNull()?.message}")
            if (runAttemptCount < 6) Result.retry() else Result.failure()
        }
    }
}

class ReleaseWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val pid = inputData.getString("p") ?: return Result.failure()
        val mac = inputData.getString("m") ?: return Result.failure()
        val r = Bg.withRouter(pid) { it.setBlocked(mac, false).getOrThrow() }
        return if (r.isSuccess) Result.success() else if (runAttemptCount < 4) Result.retry() else Result.failure()
    }
}

class ScheduleWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        var all = Bg.schedules()
        var failed = false
        for (s in all) {
            val want = s.inWindow(now)
            if (want == s.active) continue
            val r = Bg.withRouter(s.profileId) { it.setBlocked(s.mac, want).getOrThrow() }
            if (r.isSuccess) {
                all = all.map { if (it.id == s.id) it.copy(active = want) else it }
                val text = applicationContext.getString(if (want) R.string.evt_schedule_blocked else R.string.evt_auto_unblocked, s.name.ifBlank { s.mac })
                Bg.notify(applicationContext, text)
            } else { failed = true; Diag.add("schedule failed ${s.mac}: ${r.exceptionOrNull()?.message}") }
        }
        // merge states without clobbering schedules added meanwhile
        val fresh = Bg.schedules().map { f -> all.firstOrNull { it.id == f.id }?.let { f.copy(active = it.active) } ?: f }
        val a = JSONArray()
        fresh.forEach { a.put(JSONObject().put("id", it.id).put("p", it.profileId).put("m", it.mac).put("n", it.name).put("s", it.startMin).put("e", it.endMin).put("a", it.active)) }
        applicationContext.getSharedPreferences("bg", Context.MODE_PRIVATE).edit().putString("schedules", a.toString()).apply()
        val next = Bg.nextBoundaryDelayMin(fresh)
        if (next > 0) {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "schedules-next", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ScheduleWorker>().setInitialDelay(next, TimeUnit.MINUTES).build(),
            )
        }
        return if (failed && runAttemptCount < 4) Result.retry() else Result.success()
    }
}
