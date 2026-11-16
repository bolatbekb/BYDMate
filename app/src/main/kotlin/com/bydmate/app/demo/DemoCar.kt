package com.bydmate.app.demo

import com.bydmate.app.data.autoservice.BatteryReading
import com.bydmate.app.data.remote.DiParsData
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A simulated Leopard 3 on an endless drive: speed and power wander smoothly, the odometer
 * and the lifetime energy counter grow with them, SOC falls with the energy used. Every
 * snapshot is consistent with the previous one, so the app's own trip, range and consumption
 * logic works on it exactly as on a real car.
 */
object DemoCar {
    private const val CAPACITY_KWH = 72.9
    private const val ODOMETER_START_KM = 17_057.4
    private const val LIFETIME_KWH_START = 2_650.0
    private const val SOC_START = 78.0
    private const val ENGINE_PERIOD_MS = 10_000L

    private val startMs = System.currentTimeMillis()
    private var lastMs = startMs
    private var odometerKm = ODOMETER_START_KM
    private var usedKwh = 0.0

    @Synchronized
    private fun step(nowMs: Long): Triple<Double, Double, Double> {
        val t = (nowMs - startMs) / 1000.0
        val speed = max(0.0, 58 + 32 * sin(t / 23) + 9 * sin(t / 7))
        // Cruise draw ~16 kWh/100 km, plus auxiliaries, with regen dips when the speed drops.
        val powerKw = speed * 0.16 + 2.5 + 9 * sin(t / 5.3)
        val dtH = ((nowMs - lastMs).coerceIn(0, 10_000)) / 3_600_000.0
        odometerKm += speed * dtH
        usedKwh += max(powerKw, -40.0) * dtH
        lastMs = nowMs
        return Triple(speed, powerKw, t)
    }

    private val soc: Double get() = (SOC_START - usedKwh / CAPACITY_KWH * 100).coerceIn(5.0, 100.0)

    @Suppress("LongMethod") // one value per DiParsData field
    fun snapshot(nowMs: Long = System.currentTimeMillis()): DiParsData {
        val (speed, powerKw, t) = step(nowMs)
        val hvVoltage = 395 + (soc / 10).roundToInt()
        val rpm = engineRpm(nowMs)
        return DiParsData(
            soc = soc.roundToInt(),
            speed = speed.roundToInt(),
            mileage = odometerKm,
            power = powerKw,
            chargeGunState = 1,
            maxBatTemp = 28,
            avgBatTemp = 27,
            minBatTemp = 25,
            chargingStatus = 0,
            batteryCapacityKwh = CAPACITY_KWH,
            totalElecConsumption = LIFETIME_KWH_START + usedKwh,
            voltage12v = 13.9,
            maxCellVoltage = 3.342,
            minCellVoltage = 3.331,
            exteriorTemp = 7,
            gear = 4,
            powerState = 2,
            insideTemp = 21,
            acStatus = 1,
            acTemp = 21,
            fanLevel = 3,
            acCirc = 0,
            doorFL = 0, doorFR = 0, doorRL = 0, doorRR = 0,
            windowFL = 0, windowFR = 0, windowRL = 0, windowRR = 0,
            sunroof = 0,
            trunk = 2,
            hood = 0,
            seatbeltFL = 1,
            lockFL = 2,
            tirePressFL = 248, tirePressFR = 250, tirePressRL = 245, tirePressRR = 246,
            driveMode = 3,
            workMode = if (rpm > 0) 3 else 1,
            autoPark = 0,
            rain = 0,
            lightLow = 1,
            drl = 1,
            seatbeltFR = 1,
            occupancyFL = 2, occupancyFR = 2, occupancyRL = 1, occupancyRM = 1, occupancyRR = 1,
            seatHeatDriver = 1,
            lightLevel = 3,
            keyBatteryStatus = 0,
            turnSignal = 1,
            insulationKohm = 2_800,
            engineRpm = rpm,
            motorTempFront = 52, motorTempRear = 61,
            inverterTempFront = 44, inverterTempRear = 47,
            hvVoltage = hvVoltage,
            hvCurrent = powerKw * 1000 / hvVoltage,
            batteryPowerW = powerKw * 1000,
            bmsMaxChargeKw = 150.0,
            bmsMaxDischargeAllowKw = 230.0,
            motorRpmFront = (speed * 95).roundToInt(),
            motorRpmRear = (speed * 95).roundToInt(),
            motorCurrentFront = (powerKw * 0.35 * 1000 / hvVoltage).toFloat(),
            motorCurrentRear = (powerKw * 0.65 * 1000 / hvVoltage).toFloat(),
            compressorW = 900 + (300 * sin(t / 11)).roundToInt(),
            tyreTempFL = 24, tyreTempFR = 25, tyreTempRL = 23, tyreTempRR = 24,
            pedalAccel = (20 + 15 * sin(t / 4)).roundToInt().coerceIn(0, 100),
            pedalBrake = 0,
            frontTrunk = 0,
            batteryRemainKwh = soc / 100 * CAPACITY_KWH,
            seatbeltRL = 0, seatbeltRM = 0, seatbeltRR = 0,
            // Hybrid readings, so the retro home screens can show the tank on an emulator.
            fuelPercent = 64,
            fuelRangeKm = 540,
            evRangeKm = (soc / 100 * 125).roundToInt(),
        )
    }

    /**
     * The engine runs every other 10 s (by wall clock), idling around 1 150–1 450 rpm, so the
     * HEV chip and the fuel dial's rpm ring can be seen switching on an emulator.
     */
    internal fun engineRpm(nowMs: Long): Int =
        if ((nowMs / ENGINE_PERIOD_MS) % 2 == 0L) 0 else (1_300 + 150 * sin(nowMs / 3_000.0)).roundToInt()

    fun battery(nowMs: Long = System.currentTimeMillis()): BatteryReading {
        step(nowMs)
        return BatteryReading(
            sohPercent = 98.6f,
            socPercent = soc.toFloat(),
            lifetimeKwh = (LIFETIME_KWH_START + usedKwh).toFloat(),
            lifetimeMileageKm = odometerKm.toFloat(),
            voltage12v = 13.9f,
            readAtMs = nowMs,
        )
    }
}
