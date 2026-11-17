package com.bydmate.app.ui.home.graphite

import android.content.Context
import com.bydmate.app.demo.DemoMode
import com.bydmate.app.service.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The live trip for the whole process: fed by the tracking service's readings whether or not a
 * home screen is visible, saved to prefs so a restart keeps an open session.
 */
object LiveTrip {
    private const val PREFS = "bydmate_live_trip"
    private const val PREFS_DEMO = "bydmate_live_trip_demo"
    private const val KEY_STATE = "state"
    private const val PUBLISH_EVERY_MS = 10_000L
    private const val SAVE_EVERY_MS = 60_000L

    private val _state = MutableStateFlow(LiveTripState())
    val state: StateFlow<LiveTripState> = _state.asStateFlow()

    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val name = if (DemoMode.isOn(context)) PREFS_DEMO else PREFS
        val prefs = runCatching { context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE) }.getOrNull()
        val raw = runCatching { prefs?.getString(KEY_STATE, null) }.getOrNull()
        val restored = LiveTripCodec.decode(raw)
        val tracker = LiveTripTracker(restored, System.currentTimeMillis())
        _state.value = tracker.state
        fun save() {
            runCatching { prefs?.edit()?.putString(KEY_STATE, LiveTripCodec.encode(tracker.state))?.apply() }
        }
        if (tracker.state != restored) save()
        var lastPublishMs = System.currentTimeMillis()
        var lastSaveMs = lastPublishMs
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            TrackingService.lastData.filterNotNull().collect { data ->
                val now = System.currentTimeMillis()
                val transition = tracker.onReading(data, now)
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
