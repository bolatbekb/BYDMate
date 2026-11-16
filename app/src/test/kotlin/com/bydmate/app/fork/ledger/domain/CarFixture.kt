package com.bydmate.app.fork.ledger.domain

import com.bydmate.app.data.remote.DiParsData

/** DiParsData with everything null except the named overrides. */
internal fun car(mileage: Double? = null, fuelPercent: Int? = null, fuelTotalL: Double? = null) = DiParsData(
    mileage = mileage, soc = null, speed = null, power = null, chargeGunState = null, maxBatTemp = null, avgBatTemp = null, minBatTemp = null, chargingStatus = null, batteryCapacityKwh = null, totalElecConsumption = null, voltage12v = null, maxCellVoltage = null, minCellVoltage = null, exteriorTemp = null, gear = null, powerState = null, insideTemp = null, acStatus = null, acTemp = null, fanLevel = null, acCirc = null, doorFL = null, doorFR = null, doorRL = null, doorRR = null, windowFL = null, windowFR = null, windowRL = null, windowRR = null, sunroof = null, trunk = null, hood = null, seatbeltFL = null, lockFL = null, tirePressFL = null, tirePressFR = null, tirePressRL = null, tirePressRR = null, driveMode = null, workMode = null, autoPark = null, rain = null, lightLow = null, drl = null,
    fuelPercent = fuelPercent, fuelTotalL = fuelTotalL,
)
