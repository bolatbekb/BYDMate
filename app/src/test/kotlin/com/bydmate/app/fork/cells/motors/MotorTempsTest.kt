package com.bydmate.app.fork.cells.motors

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.ui.tech.TechPanelUiState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotorTempsTest {

    @Test fun `no data and out of range read as a dash`() {
        assertNull(motorTempOrNull(-40))
        assertNull(motorTempOrNull(65535))
        assertNull(motorTempOrNull(151))
        assertEquals(-39, motorTempOrNull(-39))
        assertEquals(44, motorTempOrNull(44))
    }

    @Test fun `the four 0x4DA fids are read on the Gb device with getInt`() {
        assertEquals(listOf(0x4DA00100, 0x4DA00118, 0x4DA00164, 0x4DA0016C), MOTOR_TEMP_ITEMS.map { it.fid })
        MOTOR_TEMP_ITEMS.forEach {
            assertEquals(1039, it.dev)
            assertEquals(5, it.tx)
        }
    }

    private fun reader(reply: List<Pair<Int, Int>>?): MotorTempsReader {
        val helper = mockk<HelperClient>()
        coEvery { helper.readBatch(MOTOR_TEMP_ITEMS) } returns reply
        return HelperMotorTempsReader(helper)
    }

    @Test fun `helper reader maps the reply in order and drops bad values one by one`() = runBlocking {
        assertEquals(MotorTemps(26, 31, 27, 38), reader(listOf(0 to 26, 0 to 31, 0 to 27, 0 to 38)).read())
        assertEquals(MotorTemps(null, 31, 27, null), reader(listOf(-1 to 26, 0 to 31, 0 to 27, 0 to -40)).read())
        assertNull(reader(null).read())
        assertNull(reader(listOf(0 to 26)).read())
    }

    @Test fun `demo reader takes the demo car's values`() = runBlocking {
        val live = MutableStateFlow<DiParsData?>(
            DiParsData(
                mileage = null, soc = null, speed = null, power = null, chargeGunState = null, maxBatTemp = null, avgBatTemp = null, minBatTemp = null, chargingStatus = null, batteryCapacityKwh = null, totalElecConsumption = null, voltage12v = null, maxCellVoltage = null, minCellVoltage = null, exteriorTemp = null, gear = null, powerState = null, insideTemp = null, acStatus = null, acTemp = null, fanLevel = null, acCirc = null, doorFL = null, doorFR = null, doorRL = null, doorRR = null, windowFL = null, windowFR = null, windowRL = null, windowRR = null, sunroof = null, trunk = null, hood = null, seatbeltFL = null, lockFL = null, tirePressFL = null, tirePressFR = null, tirePressRL = null, tirePressRR = null, driveMode = null, workMode = null, autoPark = null, rain = null, lightLow = null, drl = null,
                inverterTempFront = 33, motorTempFront = 40, inverterTempRear = 35, motorTempRear = 42,
            ),
        )
        assertEquals(MotorTemps(33, 40, 35, 42), DemoMotorTempsReader(live).read())
        live.value = null
        assertNull(DemoMotorTempsReader(live).read())
    }

    @Test fun `leopard 5 card takes the 0x4DA temperatures and shows rpm as magnitudes`() {
        // The author's fids on this car: front −40 is filtered to null upstream, rear inverter 200.
        val state = TechPanelUiState(
            inverterTempFront = null, motorTempFront = null, inverterTempRear = 200, motorTempRear = 12,
            motorRpmFront = -5793, motorRpmRear = 6052, engineRpm = 1450,
        )
        val out = applyLeopard5Motors(state, MotorTemps(inverterFront = 26, motorFront = 37, inverterRear = 30, motorRear = 39))
        assertEquals(26, out.inverterTempFront)
        assertEquals(37, out.motorTempFront)
        assertEquals(30, out.inverterTempRear)
        assertEquals(39, out.motorTempRear)
        assertEquals(5793, out.motorRpmFront)
        assertEquals(6052, out.motorRpmRear)
        assertEquals(1450, out.engineRpm)
    }

    @Test fun `reverse reads positive too, and an unread card shows dashes not the wrong fids`() {
        val state = TechPanelUiState(inverterTempRear = 200, motorRpmFront = 384, motorRpmRear = -388)
        val out = applyLeopard5Motors(state, null)
        assertNull(out.inverterTempRear)
        assertEquals(384, out.motorRpmFront)
        assertEquals(388, out.motorRpmRear)
    }

    @Test fun `at-rest electric-motor dither below 10 reads 0, engine rpm is untouched`() {
        val state = TechPanelUiState(motorRpmFront = 0, motorRpmRear = -1, engineRpm = 3)
        val out = applyLeopard5Motors(state, null)
        assertEquals(0, out.motorRpmFront)
        assertEquals(0, out.motorRpmRear)
        assertEquals(3, out.engineRpm) // ICE, not an electric motor — no noise floor
    }

    @Test fun `rpm noise floor clamps under 10 and keeps 10 and up as a magnitude`() {
        assertEquals(0, denoiseMotorRpm(9))
        assertEquals(0, denoiseMotorRpm(-9))
        assertEquals(10, denoiseMotorRpm(10))
        assertEquals(10, denoiseMotorRpm(-10))
        assertEquals(5793, denoiseMotorRpm(-5793))
        assertNull(denoiseMotorRpm(null))
    }
}
