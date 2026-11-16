package com.bydmate.app.ui.home

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.ui.dashboard.DashboardUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** A tab the retro homes draw as a key on the deck: the author's bottom-bar items minus Home. */
data class RetroTab(
    val route: String,
    val label: String,
    /** The same icon as the author's bottom-bar item. */
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

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
    /** Drive profile the car runs now: "ECO", "NORMAL", "SPORT", a terrain mode name, or null. */
    val driveProfile: String?,
    /** Everyday profiles this car has (ECO / NORMAL / SPORT, in that order); empty when unknown. */
    val driveProfiles: List<String>,
    /** Share of traction on the front motor, 0..1; null when unknown. */
    val frontShare: Float?,
    val periodCost: String,
    val periodKm: String,
    /** kWh/100 for each of the last seven days, oldest first; null for a day without trips. */
    val weekPer100: List<Double?>,
    val events: List<HomeEvent>,
    val currency: String,
    /** Engine speed, rpm; 0 with the engine off, null when the car does not report it. */
    val engineRpm: Int? = null,
    /** AC / heat pump compressor draw, kW. */
    val compressorKw: Double? = null,
    /** Climate on; null when unknown. */
    val acOn: Boolean? = null,
    /** Climate in AUTO; null when unknown. */
    val acAuto: Boolean? = null,
    /** Kilometres driven today (see DayMileage). */
    val todayKm: Double? = null,
    /** Gear: 1=P, 2=R, 3=N, 4=D; null when unknown. */
    val gear: Int? = null,
    /** Power state: 0=OFF, 1=ON, 2=DRIVE; null when unknown. */
    val powerState: Int? = null,
    /** Auto hold: 0=disabled, 1=standby, 2=active; null when unknown. */
    val autoPark: Int? = null,
    /** Climate set temperature, °C. */
    val acTemp: Int? = null,
    /** 0/1/2, 2 = charging now; null when unknown. */
    val chargingStatus: Int? = null,
    /** Traction battery power, kW (+ draw / − charge). */
    val batteryPowerKw: Double? = null,
    /** Battery capacity the car reports, kWh. */
    val liveCapacityKwh: Double? = null,
    /** 12 V battery, V. */
    val voltage12v: Double? = null,
    /** HV pack to body insulation resistance, kΩ. */
    val insulationKohm: Int? = null,
) {
    companion object {
        private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        @Suppress("CyclomaticComplexMethod", "LongMethod") // a flat chain of null fallbacks, one per field
        fun from(
            s: DashboardUiState,
            live: DiParsData?,
            supported: List<com.bydmate.app.data.vehicle.DriveMode>? = null,
            todayKm: Double? = null,
        ): HomeData {
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
                driveProfile = com.bydmate.app.fork.DriveModeSupport.current(live)?.name,
                driveProfiles = supported.orEmpty().map { it.name },
                frontShare = front,
                periodCost = String.format(Locale.getDefault(), "%.0f %s", s.totalCost, s.currencySymbol),
                periodKm = String.format(Locale.getDefault(), "%.1f км", s.totalKm),
                weekPer100 = week,
                events = events,
                currency = s.currencySymbol,
                engineRpm = s.engineRpm ?: live?.engineRpm,
                compressorKw = live?.compressorW?.let { it / 1000.0 },
                acOn = live?.acStatus?.let { it == 1 },
                acAuto = live?.acCtrlMode?.let { it == 0 },
                todayKm = todayKm,
                gear = live?.gear,
                powerState = live?.powerState,
                autoPark = live?.autoPark,
                acTemp = live?.acTemp,
                chargingStatus = live?.chargingStatus,
                batteryPowerKw = live?.batteryPowerW?.let { it / 1000.0 },
                liveCapacityKwh = live?.batteryCapacityKwh,
                voltage12v = live?.voltage12v,
                insulationKohm = live?.insulationKohm,
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
