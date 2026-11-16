package com.bydmate.app.fork.ledger.domain

import com.bydmate.app.data.remote.DiParsData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerParkingTest {
    private fun d(speed: Int?, gear: Int?) = DiParsData(
        speed = speed, soc = null, mileage = null, power = null,
        chargeGunState = null, maxBatTemp = null, avgBatTemp = null,
        minBatTemp = null, chargingStatus = null, batteryCapacityKwh = null,
        totalElecConsumption = null, voltage12v = null, maxCellVoltage = null,
        minCellVoltage = null, exteriorTemp = null, gear = gear, powerState = null,
        insideTemp = null, acStatus = null, acTemp = null, fanLevel = null,
        acCirc = null, doorFL = null, doorFR = null, doorRL = null, doorRR = null,
        windowFL = null, windowFR = null, windowRL = null, windowRR = null,
        sunroof = null, trunk = null, hood = null, seatbeltFL = null,
        lockFL = null, tirePressFL = null, tirePressFR = null,
        tirePressRL = null, tirePressRR = null, driveMode = null,
        workMode = null, autoPark = null, rain = null, lightLow = null, drl = null,
    )
    @Test fun nullIsParked() { assertTrue(LedgerParking.isParked(null)) }
    @Test fun parkGear() { assertTrue(LedgerParking.isParked(d(0, 1))) }
    @Test fun driveGear() { assertFalse(LedgerParking.isParked(d(0, 4))) }
    @Test fun reverseGear() { assertFalse(LedgerParking.isParked(d(0, 2))) }
    @Test fun moving() { assertFalse(LedgerParking.isParked(d(12, 1))) }
    @Test fun unknownIsParked() { assertTrue(LedgerParking.isParked(d(null, null))) }
}
