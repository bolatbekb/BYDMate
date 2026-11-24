package com.bydmate.app.fork.hybrid

import com.bydmate.app.data.autoservice.AutoserviceClient
import com.bydmate.app.data.autoservice.BatteryReading
import com.bydmate.app.data.autoservice.ChargingReading
import com.bydmate.app.data.charging.AutoserviceChargingDetector
import com.bydmate.app.data.charging.CatchUpJournal
import com.bydmate.app.data.charging.CatchUpOutcome
import com.bydmate.app.data.charging.ChargingStateStore
import com.bydmate.app.data.charging.ChargingTypeClassifier
import com.bydmate.app.data.local.LocalePreferences
import com.bydmate.app.data.local.dao.ChargeDao
import com.bydmate.app.data.local.dao.FakeSettingsDao
import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.nativestack.kindMonitor
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.diParsData
import com.bydmate.app.data.repository.ChargeRepository
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.fork.ledger.domain.DetectorAction
import com.bydmate.app.fork.ledger.domain.DetectorState
import com.bydmate.app.fork.ledger.domain.RefuelDetector
import com.bydmate.app.fork.ledger.domain.TankReading
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A SOC rise while a hybrid drives (regen downhill, the engine charging) is no charging session, and a tank-level rise
 * while it drives is no refuel; a real plug charge and a real refuel are still found. Checked with the app alive, the
 * way the drive of 2026-11-23 ran (the anchor followed every regen +1 % on the road).
 */
class HybridChargeVsDriveTest {

    private class Car(var soc: Float, var km: Float) : AutoserviceClient {
        override suspend fun isAvailable() = true
        override suspend fun getInt(dev: Int, fid: Int): Int? = null
        override suspend fun getIntRaw(dev: Int, fid: Int): Int? = null
        override suspend fun getFloat(dev: Int, fid: Int): Float? = null
        override suspend fun readBatterySnapshot() = BatteryReading(
            sohPercent = 100f, socPercent = soc, lifetimeKwh = null, lifetimeMileageKm = km, voltage12v = 14f, readAtMs = 0L,
        )
        override suspend fun readChargingSnapshot() = ChargingReading(
            gunConnectState = 1, chargingType = 1, chargeBatteryVoltV = 0, batteryType = 1,
            chargingCapacityKwh = null, bmsState = null, readAtMs = 0L,
        )
    }

    private val chargeDao = mockk<ChargeDao>(relaxed = true).also { coEvery { it.insert(any()) } returns 1L }

    private fun detector(car: Car, anchorSoc: Int, anchorKm: Float): Pair<AutoserviceChargingDetector, ChargingStateStore> {
        val settings = SettingsRepository(
            FakeSettingsDao(
                mapOf(
                    SettingsRepository.KEY_CHARGING_BASELINE_SOC to anchorSoc.toString(),
                    SettingsRepository.KEY_LAST_MILEAGE_KM to anchorKm.toString(),
                    SettingsRepository.KEY_BATTERY_CAPACITY to "31.8",
                )
            ),
            mockk<LocalePreferences>(relaxed = true),
        )
        val store = ChargingStateStore(settings)
        val d = AutoserviceChargingDetector(
            client = car, chargeRepo = ChargeRepository(chargeDao), batteryHealthRepo = mockk(relaxed = true),
            stateStore = store, classifier = ChargingTypeClassifier(), settings = settings,
            costCalculator = mockk(relaxed = true), parsReader = mockk(relaxed = true),
            journal = CatchUpJournal(settings), vehicleKind = kindMonitor(VehicleKind.HYBRID),
        )
        return d to store
    }

    private fun tick(soc: Int, km: Double, speed: Int, rpm: Int?, power: Double): DiParsData =
        diParsData(soc = soc, mileage = km, speed = speed, power = power, chargeGunState = 1).copy(engineRpm = rpm)

    @Test fun `downhill regen with the engine off - the anchor follows, no charge row`() = runTest {
        val car = Car(soc = 60f, km = 1000f)
        val (d, store) = detector(car, anchorSoc = 60, anchorKm = 1000f)
        var rearm = false
        for (i in 1..4) {
            rearm = rearm || d.recordParkedAnchor(tick(60 + i, 1000.0 + i, speed = 50, rpm = 0, power = -30.0), now = i * 60_000L)
        }
        rearm = rearm || d.recordParkedAnchor(tick(64, 1005.0, speed = 40, rpm = 0, power = 5.0), now = 300_000L)
        assertFalse(rearm)
        assertEquals(64, store.load().socPercent)
        car.soc = 64f
        car.km = 1005f
        assertEquals(CatchUpOutcome.NO_DELTA, d.runCatchUp(now = 400_000L).outcome)
        coVerify(exactly = 0) { chargeDao.insert(any()) }
    }

    @Test fun `the engine charges the pack while the car stands - no charge row`() = runTest {
        val car = Car(soc = 50f, km = 1000f)
        val (d, store) = detector(car, anchorSoc = 50, anchorKm = 1000f)
        for (s in 51..55) d.recordParkedAnchor(tick(s, 1000.0, speed = 0, rpm = 1300, power = -20.0), now = s * 60_000L)
        assertEquals(55, store.load().socPercent)
        car.soc = 55f
        assertEquals(CatchUpOutcome.NO_DELTA, d.runCatchUp(now = 4_000_000L).outcome)
        coVerify(exactly = 0) { chargeDao.insert(any()) }
    }

    @Test fun `a plug charge while the head unit slept is still a charge`() = runTest {
        val car = Car(soc = 80f, km = 1000f)
        val (d, _) = detector(car, anchorSoc = 30, anchorKm = 1000f)
        assertEquals(CatchUpOutcome.SESSION_CREATED, d.runCatchUp(now = 8 * 3_600_000L).outcome)
        coVerify(exactly = 1) { chargeDao.insert(any()) }
    }

    @Test fun `a refuel stop leaves the SOC as it was - no charge row`() = runTest {
        val car = Car(soc = 42f, km = 1000f)
        val (d, _) = detector(car, anchorSoc = 42, anchorKm = 1000f)
        assertEquals(CatchUpOutcome.NO_DELTA, d.runCatchUp(now = 600_000L).outcome)
        coVerify(exactly = 0) { chargeDao.insert(any()) }
    }

    @Test fun `driving with the SOC rising and the tank falling is no refuel`() {
        var state: DetectorState? = null
        var drafts = 0
        for (i in 0..30) {
            val (next, action) = RefuelDetector.step(state, TankReading(60 - i / 10, 858.0 + i * 0.05, 1000.0 + i, i * 60_000L), 10)
            state = next
            if (action !is DetectorAction.None) drafts++
        }
        assertEquals(0, drafts)
    }

    @Test fun `the engine running on the spot drains the tank - no refuel`() {
        var state: DetectorState? = null
        var drafts = 0
        for (i in 0..20) {
            val (next, action) = RefuelDetector.step(state, TankReading(60 - i / 8, 858.0 + i * 0.03, 1000.0, i * 60_000L), 10)
            state = next
            if (action !is DetectorAction.None) drafts++
        }
        assertEquals(0, drafts)
    }

    @Test fun `a real refuel at a standstill is found`() {
        val (s1, _) = RefuelDetector.step(null, TankReading(20, 858.0, 1000.0, 0L), 10)
        val (_, action) = RefuelDetector.step(s1, TankReading(95, 858.0, 1000.1, 300_000L), 10)
        assertTrue(action is DetectorAction.CreateDraft)
    }
}
