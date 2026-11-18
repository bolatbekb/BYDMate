package com.bydmate.app.ui.home.graphite

import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.flow.combine
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
        val tracker = LiveTripTracker(restored.state, System.currentTimeMillis())
        val clock = BootClock(context.applicationContext)
        val startMs = System.currentTimeMillis()
        val boot0 = clock.read(startMs)
        TripLog.event(
            startMs, "process_start",
            listOf(
                "demo" to demo, "boot" to boot0.bootCount, "bootAt" to TripLogFormat.clock(boot0.bootMs, ZoneId.systemDefault()),
                "sleepS" to boot0.sleepMs / MS_PER_S, "uptimeS" to (startMs - boot0.bootMs - boot0.sleepMs) / MS_PER_S,
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
            combine(TrackingService.lastData, TrackingService.sessionStartedAt, ticks) { data, anchor, _ -> anchor to data }
                .collect { (anchor, data) ->
                    val now = System.currentTimeMillis()
                    val before = tracker.state
                    val transition = tracker.onTick(clock.read(now), anchor, data, now)
                    // Logging must never cost the trip.
                    runCatching {
                        TripLog.tick(now, TripTick.of(anchor, data, tracker.state.open), tracker.state.open != null)
                        (tracker.drainEvents() + TripLogEvents.diff(before, tracker.state)).forEach { TripLog.event(now, it.name, it.fields) }
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

    /** Reads the head unit's boot: `BOOT_COUNT` (null if unreadable), the wall time of the boot, the time asleep. */
    private class BootClock(private val context: Context) {
        fun read(nowMs: Long): BootInfo {
            val elapsed = SystemClock.elapsedRealtime()
            val count = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()
            return BootInfo(count, nowMs - elapsed, elapsed - SystemClock.uptimeMillis())
        }
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
