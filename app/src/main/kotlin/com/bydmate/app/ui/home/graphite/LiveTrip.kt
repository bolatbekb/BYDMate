package com.bydmate.app.ui.home.graphite

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.service.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The live trip for the whole process: fed by the tracking service's readings and its ignition session whether or
 * not a home screen is visible, saved to prefs so a restart keeps an open session.
 */
object LiveTrip {
    private const val PREFS = "bydmate_live_trip"
    private const val PREFS_DEMO = "bydmate_live_trip_demo"
    private const val PREFS_FINISHED = "bydmate_live_trip_finished"
    private const val PREFS_FINISHED_DEMO = "bydmate_live_trip_finished_demo"
    private const val KEY_LEGACY = "state"
    private const val KEY_OPEN = "open"
    private const val KEY_FINISHED = "finished"
    private const val TICK_MS = 10_000L
    private const val PUBLISH_EVERY_MS = 10_000L
    private const val SAVE_EVERY_MS = 10_000L
    private const val SAMPLE_MS = 1_000L
    private const val MS_PER_S = 1000L

    private val _state = MutableStateFlow(LiveTripState())
    val state: StateFlow<LiveTripState> = _state.asStateFlow()

    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val demo = DemoMode.isOn(context)
        TripLog.init(context, demo)
        val store = PrefsStore(context.applicationContext, demo)
        val restored = store.restore()
        val clock = BootClock(context.applicationContext)
        val startMs = System.currentTimeMillis()
        val boot0 = clock.read()
        val exit = clock.lastExit()
        val tracker = LiveTripTracker(restored.state, startMs, exit)
        TripLog.event(
            startMs, "process_start",
            listOf(
                "demo" to demo, "exitReason" to exit?.reasonCode, "exitDesc" to exit?.description?.replace(' ', '_'),
                "exitAt" to exit?.atMs?.let { TripLogFormat.clock(it, ZoneId.systemDefault()) }, "bootCount" to boot0.bootCount,
                "elapsedS" to boot0.elapsedMs / MS_PER_S, "uptimeS" to boot0.uptimeMs / MS_PER_S, "sleepS" to boot0.sleepMs / MS_PER_S,
                "lastElapsedS" to restored.state.open?.elapsedAtLastMs?.div(MS_PER_S),
                "lastSleepS" to restored.state.open?.sleepAtLastMs?.div(MS_PER_S), "closed" to (restored.state.closed != null),
            ),
        )
        restored.state.open?.let {
            TripLog.event(
                System.currentTimeMillis(), "restore",
                listOf("start" to it.startMs, "lastSeen" to it.lastSeenMs, "finished" to restored.state.finished.size),
            )
        }
        _state.value = tracker.state
        TripBackfill.start(context)
        if (tracker.state != restored.state || restored.migrated) store.save(tracker.state)
        var lastPublishMs = System.currentTimeMillis()
        var lastSaveMs = lastPublishMs
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val ticks = flow { while (true) { emit(Unit); delay(TICK_MS) } }
            beats(TrackingService.lastData, TrackingService.sessionStartedAt, ticks)
                .collect { anchor ->
                    // read live on every beat, the time first: an identical poll changes no data but still advances it
                    val (data, dataAtMs) = liveReading(TrackingService.lastData) { TrackingService.lastDataAtMs }
                    val now = System.currentTimeMillis()
                    val before = tracker.state
                    val transition = tracker.onTick(clock.read(), anchor, data, now, dataAtMs)
                    val cycleEvents = tracker.drainEvents()
                    // Logging must never cost the trip.
                    runCatching {
                        TripLog.tick(now, TripTick.of(anchor, data, tracker.state.open, tracker.lastTickFresh), tracker.state.open != null)
                        (cycleEvents + TripLogEvents.diff(before, tracker.state)).forEach { TripLog.event(now, it.name, it.fields) }
                    }
                    if (shouldPublish(transition, now, lastPublishMs)) {
                        lastPublishMs = now
                        _state.value = tracker.state
                    }
                    if (shouldSave(transition, now, lastSaveMs)) {
                        lastSaveMs = now
                        store.save(tracker.state)
                    }
            }
        }
    }

    /**
     * When the tracker runs: a data change (at most once a second, the first at once), a session anchor change, or the
     * 10-s ticker. The sample only limits the rate; the reading itself is taken live by [liveReading].
     */
    @OptIn(FlowPreview::class)
    internal fun <T, A> beats(data: Flow<T>, anchor: Flow<A>, ticks: Flow<Unit>): Flow<A> {
        val changes = data.map { }.sample(SAMPLE_MS).onStart { emit(Unit) }
        return combine(changes, anchor, ticks) { _, a, _ -> a }
    }

    /**
     * The reading as the tracker needs it: its poll time FIRST, then the data. The writer sets the data before the
     * time, so this order can only date a reading early, never lose one. A StateFlow drops a poll equal to the last
     * one but its time still advances, which is what keeps an identical standstill poll fresh.
     */
    internal fun <T> liveReading(data: StateFlow<T>, atMs: () -> Long): Pair<T, Long> {
        val at = atMs()
        return data.value to at
    }

    /** Reads the head unit's clocks and how the previous process ended. */
    private class BootClock(private val context: Context) {
        fun read(): BootInfo {
            val elapsed = SystemClock.elapsedRealtime()
            val count = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
            return BootInfo(count, elapsed, elapsed - SystemClock.uptimeMillis())
        }

        /** The newest exit record of this app (API 30+): the head unit's quickboot kill shows here. */
        fun lastExit(): ExitInfo? = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
                ?.let { ExitInfo(it.reason, it.description, it.timestamp) }
        }.getOrNull()
    }

    internal fun shouldPublish(transition: Boolean, nowMs: Long, lastPublishMs: Long): Boolean =
        transition || nowMs - lastPublishMs >= PUBLISH_EVERY_MS

    internal fun shouldSave(transition: Boolean, nowMs: Long, lastSaveMs: Long): Boolean =
        transition || nowMs - lastSaveMs >= SAVE_EVERY_MS

    /** The prefs side: the open session in one file, the finished list in its own, written only when it changed. */
    private class PrefsStore(context: Context, demo: Boolean) {
        private val open = prefs(context, if (demo) PREFS_DEMO else PREFS)
        private val finished = prefs(context, if (demo) PREFS_FINISHED_DEMO else PREFS_FINISHED)
        private var savedFinished: List<TripEstimate>? = null

        private fun prefs(context: Context, file: String) =
            runCatching { context.getSharedPreferences(file, Context.MODE_PRIVATE) }.getOrNull()

        private fun read(p: SharedPreferences?, key: String) = runCatching { p?.getString(key, null) }.getOrNull()

        fun restore(): LiveTripPersistence.Restored {
            val r = LiveTripPersistence.restore(
                read(open, KEY_OPEN), read(finished, KEY_FINISHED), read(open, KEY_LEGACY), read(open, KEY_FINISHED),
            )
            if (!r.migrated) savedFinished = r.state.finished
            return r
        }

        fun save(state: LiveTripState) {
            runCatching {
                // The finished file first: a crash between the two leaves the old keys to restore from.
                val saved = savedFinished
                if (saved == null || LiveTripPersistence.shouldSaveFinished(saved, state.finished)) {
                    finished?.edit()?.putString(KEY_FINISHED, LiveTripPersistence.encodeFinished(state))?.apply()
                    savedFinished = state.finished
                }
                open?.edit()?.putString(KEY_OPEN, LiveTripPersistence.encodeOpen(state))
                    ?.remove(KEY_LEGACY)?.remove(KEY_FINISHED)?.apply()
            }
        }
    }
}
