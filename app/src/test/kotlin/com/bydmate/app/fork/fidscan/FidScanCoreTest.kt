package com.bydmate.app.fork.fidscan

import com.bydmate.app.data.remote.DiParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FidScanCoreTest {

    private val catalog = """
        BYDAutoConstants.BYDAUTO_DEVICE_ENGINE=1012
        BYDAutoConstants.BYDAUTO_DEVICE_STATISTIC=1014
        BYDAutoConstants.BYDAUTO_DEVICE_MQTT=1033
        Engine.ENGINE_ENGINE_COOLANT_TEMPERATURE=1320181824
        Engine.ENGINE_DRIVE_FORCE_ADJUST_SET=5
        Statistic.STATISTIC_WATER_TEMPERATURE=839263
        Mqtt.MQTT_X=7
        BYDAutoFeatureIds.ENGINE_ENGINE_COOLANT_TEMPERATURE=1320181824
    """.trimIndent()

    private fun di(
        soc: Int? = null,
        speed: Int? = null,
        mileage: Double? = null,
        chargeGunState: Int? = null,
        gear: Int? = null,
        powerState: Int? = null,
    ) = DiParsData(
        soc = soc,
        speed = speed,
        mileage = mileage,
        power = null,
        chargeGunState = chargeGunState,
        maxBatTemp = null,
        avgBatTemp = null,
        minBatTemp = null,
        chargingStatus = null,
        batteryCapacityKwh = null,
        totalElecConsumption = null,
        voltage12v = null,
        maxCellVoltage = null,
        minCellVoltage = null,
        exteriorTemp = null,
        gear = gear,
        powerState = powerState,
        insideTemp = null,
        acStatus = null,
        acTemp = null,
        fanLevel = null,
        acCirc = null,
        doorFL = null,
        doorFR = null,
        doorRL = null,
        doorRR = null,
        windowFL = null,
        windowFR = null,
        windowRL = null,
        windowRR = null,
        sunroof = null,
        trunk = null,
        hood = null,
        seatbeltFL = null,
        lockFL = null,
        tirePressFL = null,
        tirePressFR = null,
        tirePressRL = null,
        tirePressRR = null,
        driveMode = null,
        workMode = null,
        autoPark = null,
        rain = null,
        lightLow = null,
        drl = null,
    )

    @Test
    fun scanListKeepsReadableSymbolsOnly() {
        assertEquals(
            listOf(
                ScanItem(1012, 1320181824, "Engine.ENGINE_ENGINE_COOLANT_TEMPERATURE"),
                ScanItem(1014, 839263, "Statistic.STATISTIC_WATER_TEMPERATURE"),
            ),
            scanList(catalog),
        )
    }

    @Test
    fun scanListOfEmptyDumpIsEmpty() {
        assertTrue(scanList("").isEmpty())
    }

    @Test
    fun scanModeRules() {
        assertEquals(ScanMode.UNKNOWN, scanMode(null))
        assertEquals(ScanMode.CHARGE_AC, scanMode(di(chargeGunState = 2, powerState = 0)))
        assertEquals(ScanMode.CHARGE_DC, scanMode(di(chargeGunState = 4)))
        assertEquals(ScanMode.CHARGE_DC, scanMode(di(chargeGunState = 3)))
        assertEquals(ScanMode.V2L, scanMode(di(chargeGunState = 5)))
        assertEquals(ScanMode.OFF, scanMode(di(chargeGunState = 1, powerState = 0)))
        assertEquals(ScanMode.DRIVE, scanMode(di(gear = 4)))
        assertEquals(ScanMode.DRIVE, scanMode(di(gear = 2)))
        assertEquals(ScanMode.DRIVE, scanMode(di(gear = 1, speed = 3)))
        assertEquals(ScanMode.PARK, scanMode(di(gear = 1, speed = 0, powerState = 1)))
    }

    @Test
    fun readingTextPrintsValuesAndStatuses() {
        assertEquals("i=65535 d=1.5", readingText(Reading(0, 65535, 0, 1.5f.toRawBits())))
        assertEquals("i=!-999 d=!3", readingText(Reading(-999, 0, 3, 0)))
    }

    @Test
    fun valueLineFormat() {
        val item = ScanItem(1012, 1, "Engine.X")
        assertEquals("V 1012 Engine.X i=7 d=1.5", valueLine(item, Reading(0, 7, 0, 1.5f.toRawBits())))
    }

    @Test
    fun stateLineDashesForMissingData() {
        assertEquals(
            "S 15:00:00 mode=PARK gear=- speed=- soc=- gun=- power=- odo=-",
            stateLine("15:00:00", ScanMode.PARK, null),
        )
    }

    @Test
    fun stateLineWithData() {
        val d = di(soc = 55, speed = 3, mileage = 1234.5, chargeGunState = 1, gear = 4, powerState = 2)
        assertEquals(
            "S 15:00:00 mode=DRIVE gear=4 speed=3 soc=55 gun=1 power=2 odo=1234.5",
            stateLine("15:00:00", ScanMode.DRIVE, d),
        )
    }

    @Test
    fun sweepDueRule() {
        assertTrue(sweepDue(0, null, false))
        assertFalse(sweepDue(299_999, 0, false))
        assertTrue(sweepDue(300_000, 0, false))
        assertFalse(sweepDue(59_999, 0, true))
        assertTrue(sweepDue(60_000, 0, true))
    }
}
