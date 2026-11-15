package com.bydmate.app.ui.home

import android.content.Context
import com.bydmate.app.service.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

/** The odometer at the first reading of [day] (yyyyMMdd). */
data class DayStart(val day: Int, val odometerKm: Double)

/**
 * Kilometres driven today: the odometer now minus the first odometer reading of the day.
 * Fed by the tracking service's readings for the whole process, not only while a home screen
 * is visible, so a day that starts in another tab still counts from its first kilometre.
 */
object DayMileage {
    private const val PREFS = "bydmate_day_mileage"
    private const val KEY_DAY = "day"
    private const val KEY_ODO = "odometer"

    private val _todayKm = MutableStateFlow<Double?>(null)
    val todayKm: StateFlow<Double?> = _todayKm.asStateFlow()

    private var start: DayStart? = null
    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val prefs = runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }.getOrNull()
        start = prefs?.let { p ->
            val day = p.getInt(KEY_DAY, 0)
            if (day == 0) null else DayStart(day, Double.fromBits(p.getLong(KEY_ODO, 0L)))
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            TrackingService.lastData.map { it?.mileage?.takeIf { km -> km > 0.0 } }.filterNotNull().distinctUntilChanged().collect { odo ->
                val next = step(start, today(), odo)
                if (next != start) {
                    start = next
                    prefs?.edit()?.putInt(KEY_DAY, next.day)?.putLong(KEY_ODO, next.odometerKm.toRawBits())?.apply()
                }
                _todayKm.value = (odo - next.odometerKm).coerceAtLeast(0.0)
            }
        }
    }

    /**
     * The day's start after reading [odometerKm] on [day]: kept on the same day, restarted on a
     * new day or when the odometer reads below the stored start (another car, a reset).
     */
    internal fun step(prev: DayStart?, day: Int, odometerKm: Double): DayStart =
        if (prev == null || prev.day != day || odometerKm < prev.odometerKm) DayStart(day, odometerKm) else prev

    private fun today(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 10_000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }
}
