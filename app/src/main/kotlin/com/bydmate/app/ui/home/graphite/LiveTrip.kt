package com.bydmate.app.ui.home.graphite

import android.content.Context
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

/**
 * The live trip for the whole process: fed by the tracking service's readings and its ignition session whether or
 * not a home screen is visible, saved to prefs so a restart keeps an open session.
 */
object LiveTrip {
    private const val PREFS = "bydmate_live_trip"
    private const val PREFS_DEMO = "bydmate_live_trip_demo"
    private const val KEY_LEGACY = "state"
    private const val KEY_OPEN = "open"
    private const val KEY_FINISHED = "finished"
    private const val TICK_MS = 10_000L
    private const val PUBLISH_EVERY_MS = 10_000L
    private const val SAVE_EVERY_MS = 10_000L

    private val _state = MutableStateFlow(LiveTripState())
    val state: StateFlow<LiveTripState> = _state.asStateFlow()

    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val name = if (DemoMode.isOn(context)) PREFS_DEMO else PREFS
        val prefs = runCatching { context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE) }.getOrNull()
        fun read(key: String) = runCatching { prefs?.getString(key, null) }.getOrNull()
        val restored = LiveTripPersistence.restore(read(KEY_OPEN), read(KEY_FINISHED), read(KEY_LEGACY))
        val tracker = LiveTripTracker(restored.state, System.currentTimeMillis())
        _state.value = tracker.state
        var savedFinished: List<TripEstimate>? = if (restored.migrated) null else restored.state.finished
        fun save() {
            runCatching {
                val edit = prefs?.edit() ?: return@runCatching
                edit.putString(KEY_OPEN, LiveTripPersistence.encodeOpen(tracker.state))
                val saved = savedFinished
                if (saved == null || LiveTripPersistence.shouldSaveFinished(saved, tracker.state.finished)) {
                    edit.putString(KEY_FINISHED, LiveTripPersistence.encodeFinished(tracker.state))
                    savedFinished = tracker.state.finished
                }
                edit.remove(KEY_LEGACY).apply()
            }
        }
        if (tracker.state != restored.state || restored.migrated) save()
        var lastPublishMs = System.currentTimeMillis()
        var lastSaveMs = lastPublishMs
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val ticks = flow { while (true) { emit(Unit); delay(TICK_MS) } }
            combine(TrackingService.lastData, TrackingService.sessionStartedAt, ticks) { data, anchor, _ -> anchor to data }
                .collect { (anchor, data) ->
                    val now = System.currentTimeMillis()
                    val transition = tracker.onTick(anchor, data, now)
                    if (shouldPublish(transition, now, lastPublishMs)) {
                        lastPublishMs = now
                        _state.value = tracker.state
                    }
                    if (shouldSave(transition, now, lastSaveMs)) {
                        lastSaveMs = now
                        save()
                    }
            }
        }
    }

    internal fun shouldPublish(transition: Boolean, nowMs: Long, lastPublishMs: Long): Boolean =
        transition || nowMs - lastPublishMs >= PUBLISH_EVERY_MS

    internal fun shouldSave(transition: Boolean, nowMs: Long, lastSaveMs: Long): Boolean =
        transition || nowMs - lastSaveMs >= SAVE_EVERY_MS
}
