package com.bydmate.app.demo

import com.bydmate.app.data.local.database.AppDatabase
import com.bydmate.app.data.local.entity.BatterySnapshotEntity
import com.bydmate.app.data.local.entity.ChargeEntity
import com.bydmate.app.data.local.entity.IdleDrainEntity
import com.bydmate.app.data.local.entity.SettingEntity
import com.bydmate.app.data.local.entity.TariffPeriodEntity
import com.bydmate.app.data.local.entity.TripEntity
import com.bydmate.app.data.local.entity.TripPointEntity
import com.bydmate.app.data.repository.SettingsRepository
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fills an empty demo database with three months of a plausible EV life: commutes and weekend
 * trips with GPS routes, home AC charges and the odd DC stop, battery snapshots, night drain.
 * Deterministic (fixed seed) so screenshots compare across runs.
 */
object DemoSeeder {
    private const val CAPACITY = 72.9
    private const val DAYS = 90
    private const val HOME_RATE = 25.0
    private const val DC_RATE = 120.0
    private const val ROUTE_TRIPS = 30          // only the most recent trips get a GPS track
    private const val HOME_LAT = 43.2380
    private const val HOME_LON = 76.8890
    private const val HOUR = 3_600_000L
    private const val MIN = 60_000L

    private data class Leg(val start: Long, val end: Long, val km: Double, val kwh: Double, val socStart: Int,
                           val socEnd: Int, val avgSpeed: Double, val extStart: Int, val extEnd: Int)

    suspend fun seedIfEmpty(db: AppDatabase) {
        if (db.settingsDao().get(SettingsRepository.KEY_SETUP_COMPLETED) == "true") return
        val rnd = Random(42)
        val now = System.currentTimeMillis()
        val day0 = Calendar.getInstance().apply {
            timeInMillis = now; add(Calendar.DAY_OF_YEAR, -DAYS)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        db.tariffPeriodDao().insert(TariffPeriodEntity(startTs = day0, homeRate = HOME_RATE, dcRate = DC_RATE))

        val legs = mutableListOf<Leg>()
        var soc = 82.0
        for (d in 0..DAYS) {
            val dayStart = day0 + d * 24 * HOUR
            val dow = Calendar.getInstance().apply { timeInMillis = dayStart }.get(Calendar.DAY_OF_WEEK)
            val weekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
            // Season: colder further back, so the temperature chart has a spread.
            val ext = (2 + d * 0.18 + rnd.nextDouble(-3.0, 3.0)).roundToInt()
            val starts = if (weekend) listOf(11.0 + rnd.nextDouble(0.0, 2.0), 17.0 + rnd.nextDouble(0.0, 2.0))
            else listOf(8.0 + rnd.nextDouble(0.0, 0.7), 13.0 + rnd.nextDouble(0.0, 1.0), 18.2 + rnd.nextDouble(0.0, 1.2))
            for (h in starts) {
                val start = dayStart + (h * HOUR).toLong()
                if (start > now - 20 * MIN) continue
                val km = if (weekend) rnd.nextDouble(18.0, 85.0) else rnd.nextDouble(3.5, 24.0)
                val speed = if (km > 40) rnd.nextDouble(62.0, 88.0) else rnd.nextDouble(26.0, 44.0)
                // Colder and shorter means hungrier: 14..24 kWh/100 km.
                val per100 = 15.0 + (12 - ext).coerceAtLeast(0) * 0.35 + (if (km < 6) 4.5 else 0.0) + (if (speed > 75) 3.0 else 0.0) + rnd.nextDouble(-2.5, 4.0)
                val kwh = km * per100 / 100
                val end = start + (km / speed * HOUR).toLong()
                val socEnd = soc - kwh / CAPACITY * 100
                legs += Leg(start, end, km, kwh, soc.roundToInt(), socEnd.roundToInt(), speed, ext, ext + rnd.nextInt(0, 2))
                soc = socEnd
            }
            // Night: home AC charge when low, otherwise a bit of idle drain.
            val night = dayStart + 22 * HOUR + (rnd.nextDouble(0.0, 1.0) * HOUR).toLong()
            if (night > now) continue
            if (soc < 45) {
                val target = if (d % 9 == 0) 100.0 else 90.0
                val kwh = (target - soc) / 100 * CAPACITY
                val hours = kwh / 7.0
                val chargeId = db.chargeDao().insert(ChargeEntity(
                    startTs = night, endTs = night + (hours * HOUR).toLong(),
                    socStart = soc.roundToInt(), socEnd = target.roundToInt(),
                    kwhCharged = kwh, kwhChargedSoc = kwh, maxPowerKw = 7.2, avgPowerKw = 6.9,
                    type = "AC", cost = kwh * 1.10 * HOME_RATE, lat = HOME_LAT, lon = HOME_LON,
                    batTempAvg = 19.0, batTempMax = 22.0, batTempMin = 17.0, exteriorTemp = ext - 3,
                    detectionSource = "demo",
                ))
                db.batterySnapshotDao().insert(BatterySnapshotEntity(
                    timestamp = night, socStart = soc.roundToInt(), socEnd = target.roundToInt(), kwhCharged = kwh,
                    calculatedCapacityKwh = CAPACITY * (0.993 - d * 0.00003) + rnd.nextDouble(-0.4, 0.4),
                    sohPercent = 99.3 - d * 0.008, cellDeltaV = rnd.nextDouble(0.006, 0.014),
                    batTempAvg = 19.0, chargeId = chargeId,
                ))
                soc = target
            } else {
                val drop = rnd.nextDouble(0.3, 1.1)
                db.idleDrainDao().insert(IdleDrainEntity(
                    startTs = night, endTs = night + 9 * HOUR, socStart = soc.roundToInt(),
                    socEnd = (soc - drop).roundToInt(), kwhConsumed = drop / 100 * CAPACITY,
                ))
                soc -= drop
            }
            // A DC stop on long weekend days.
            if (weekend && d % 3 == 0) {
                val t = dayStart + 15 * HOUR
                if (t < now) {
                    val kwh = (80 - soc).coerceAtLeast(20.0) / 100 * CAPACITY
                    db.chargeDao().insert(ChargeEntity(
                        startTs = t, endTs = t + 32 * MIN, socStart = soc.roundToInt(),
                        socEnd = (soc + kwh / CAPACITY * 100).roundToInt(), kwhCharged = kwh, kwhChargedSoc = kwh,
                        maxPowerKw = 118.0, avgPowerKw = 84.0, type = "DC", cost = kwh * 1.05 * DC_RATE,
                        lat = HOME_LAT + 0.08, lon = HOME_LON - 0.11, batTempAvg = 31.0, batTempMax = 36.0,
                        batTempMin = 27.0, exteriorTemp = ext, detectionSource = "demo",
                    ))
                    soc += kwh / CAPACITY * 100
                }
            }
        }

        // Odometers run up to where the simulated car starts (DemoCar).
        var odo = 17_057.4 - legs.sumOf { it.km }
        legs.forEachIndexed { i, leg ->
            val tripId = db.tripDao().insert(TripEntity(
                startTs = leg.start, endTs = leg.end, distanceKm = leg.km, kwhConsumed = leg.kwh,
                kwhPer100km = leg.kwh / leg.km * 100, socStart = leg.socStart, socEnd = leg.socEnd,
                avgSpeedKmh = leg.avgSpeed, batTempAvg = 24.0, batTempMax = 27.0, batTempMin = 22.0,
                cost = leg.kwh * HOME_RATE, exteriorTemp = leg.extStart, exteriorTempEnd = leg.extEnd,
                odometerStartKm = odo, odometerEndKm = odo + leg.km, source = "demo",
            ))
            odo += leg.km
            if (i >= legs.size - ROUTE_TRIPS) db.tripPointDao().insertAll(route(tripId, leg, rnd))
        }

        db.settingsDao().setAll(listOf(
            SettingsRepository.KEY_BATTERY_CAPACITY to CAPACITY.toString(),
            SettingsRepository.KEY_VEHICLE_KIND to "EV",
            SettingsRepository.KEY_HOME_TARIFF to HOME_RATE.toString(),
            SettingsRepository.KEY_DC_TARIFF to DC_RATE.toString(),
            // The demo prices are in tenge.
            SettingsRepository.KEY_CURRENCY to "KZT",
            SettingsRepository.KEY_IDLE_DRAIN_CLEANUP_DONE to "true",
            SettingsRepository.KEY_CONSUMPTION_RECALC_DONE to "true",
            SettingsRepository.KEY_DEDUP_CLEANUP_DONE to "true",
            SettingsRepository.KEY_SETUP_COMPLETED to "true",
        ).map { (k, v) -> SettingEntity(key = k, value = v) })
    }

    /** A wandering loop out from home: one point every 20 s. */
    private fun route(tripId: Long, leg: Leg, rnd: Random): List<TripPointEntity> {
        val n = ((leg.end - leg.start) / 20_000L).toInt().coerceIn(12, 240)
        val heading = rnd.nextDouble(0.0, 6.28)
        val spanDeg = leg.km / 111.0 / 2.2
        return (0 until n).map { i ->
            val f = i / (n - 1.0)
            val a = heading + f * 3.6
            val r = spanDeg * sin(f * Math.PI)
            val speed = (leg.avgSpeed * (0.6 + 0.6 * sin(f * 19 + heading))).coerceAtLeast(0.0)
            TripPointEntity(
                tripId = tripId,
                timestamp = leg.start + ((leg.end - leg.start) * f).toLong(),
                lat = HOME_LAT + r * cos(a) + 0.002 * sin(f * 31),
                lon = HOME_LON + r * sin(a) * 1.35 + 0.002 * cos(f * 27),
                speedKmh = speed,
                altitude = 780.0 + 60 * sin(f * 4 + heading),
                batteryPowerW = speed * 160 + 2500,
            )
        }
    }
}
