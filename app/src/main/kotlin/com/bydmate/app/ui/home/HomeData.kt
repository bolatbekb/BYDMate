package com.bydmate.app.ui.home

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.ui.dashboard.DashboardUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** One line of the day's log on the retro screens. */
data class HomeEvent(val time: String, val title: String, val cost: String, val fuel: Boolean)

/**
 * Everything the retro home screens draw, already in display form. Null means the car did
 * not report it: the screens show a dash, never a made-up value.
 */
data class HomeData(
    val socPercent: Int?,
    val fuelPercent: Int?,
    val evRangeKm: Int?,
    val fuelRangeKm: Int?,
    val totalRangeKm: Int?,
    val insideTemp: Int?,
    val outsideTemp: Int?,
    val batteryTemp: Int?,
    val speedKmh: Int?,
    val powerKw: Double?,
    val odometerKm: Int?,
    /** "EV", "HEV" or null. */
    val driveMode: String?,
    /** Share of traction on the front motor, 0..1; null when unknown. */
    val frontShare: Float?,
    val periodCost: String,
    val periodKm: String,
    /** kWh/100 for each of the last seven days, oldest first; null for a day without trips. */
    val weekPer100: List<Double?>,
    val events: List<HomeEvent>,
    val currency: String,
) {
    companion object {
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun from(s: DashboardUiState, live: DiParsData?): HomeData {
            val ev = s.evRangeKm ?: live?.evRangeKm ?: s.estimatedRangeKm?.roundToInt()
            val fuel = s.fuelRangeKm ?: live?.fuelRangeKm
            val total = if (ev != null || fuel != null) (ev ?: 0) + (fuel ?: 0) else null
            val mf = live?.motorCurrentFront?.let { kotlin.math.abs(it) }
            val mr = live?.motorCurrentRear?.let { kotlin.math.abs(it) }
            val front = if (mf != null && mr != null && mf + mr > 0.1f) mf / (mf + mr) else null
            val mode = when (live?.workMode) { 1, 2 -> "EV"; 3 -> "HEV"; else -> null }

            val dayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val week = (6 downTo 0).map { back ->
                val from = dayStart - back * 86_400_000L
                val trips = s.recentTrips.filter { it.startTs in from until from + 86_400_000L && (it.distanceKm ?: 0.0) > 0.5 }
                val km = trips.sumOf { it.distanceKm ?: 0.0 }
                val kwh = trips.sumOf { it.kwhConsumed ?: 0.0 }
                if (km > 0.5) kwh / km * 100 else null
            }
            val events = s.recentTrips.filter { (it.distanceKm ?: 0.0) > 0.05 }.take(6).map { t ->
                val km = t.distanceKm ?: 0.0
                val liters = t.fuelLiters
                val title = buildString {
                    append(if (km >= 40) "Трасса · " else "Поездка · ")
                    append(String.format(Locale.getDefault(), "%.1f км", km))
                    if (liters != null && liters > 0) append(String.format(Locale.getDefault(), " · %.1f л", liters))
                }
                val sameDay = t.startTs >= dayStart
                HomeEvent(
                    time = if (sameDay) timeFmt.format(Date(t.startTs)) else "вчера".takeIf { t.startTs >= dayStart - 86_400_000L }
                        ?: SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(t.startTs)),
                    title = title,
                    cost = t.cost?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—",
                    fuel = (liters ?: 0.0) > 0,
                )
            }
            return HomeData(
                socPercent = s.soc ?: live?.soc,
                fuelPercent = s.fuelPercent ?: live?.fuelPercent,
                evRangeKm = ev,
                fuelRangeKm = fuel,
                totalRangeKm = total,
                insideTemp = s.insideTemp ?: live?.insideTemp,
                outsideTemp = s.exteriorTemp ?: live?.exteriorTemp,
                batteryTemp = s.avgBatTemp ?: live?.avgBatTemp,
                speedKmh = live?.speed,
                powerKw = live?.power,
                odometerKm = (s.odometer ?: live?.mileage)?.roundToInt(),
                driveMode = mode,
                frontShare = front,
                periodCost = String.format(Locale.getDefault(), "%.0f %s", s.totalCost, s.currencySymbol),
                periodKm = String.format(Locale.getDefault(), "%.1f км", s.totalKm),
                weekPer100 = week,
                events = events,
                currency = s.currencySymbol,
            )
        }
    }
}

/** "21°" or "—". */
fun Int?.deg(): String = this?.let { "$it°" } ?: "—"

/** "540" or "—". */
fun Int?.orDash(): String = this?.toString() ?: "—"

/** "02.31" from milliseconds, the way tape counters read. */
fun Long?.tapeTime(): String {
    val ms = this ?: return "--.--"
    val s = (ms / 1000).toInt()
    return String.format(Locale.US, "%02d.%02d", s / 60, s % 60)
}
