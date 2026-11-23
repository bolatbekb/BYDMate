package com.bydmate.app.fork.hybrid

import com.bydmate.app.data.local.BydTripRecord
import com.bydmate.app.data.local.EnergyDataReader
import com.bydmate.app.data.local.dao.IdleDrainDao
import com.bydmate.app.data.local.dao.TripDao
import com.bydmate.app.data.local.entity.TripEntity
import com.bydmate.app.data.local.historyImporter
import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.nativestack.kindMonitor
import com.bydmate.app.data.repository.LastSessionRepository
import com.bydmate.app.data.repository.SettingsRepository
import com.bydmate.app.data.repository.TripRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A trip from the car's own trip history (energydata): a hybrid pack gain is kept with its minus, an EV stays as it was. */
class HybridEnergyDataImportTest {

    private val energyDataReader = mockk<EnergyDataReader>(relaxed = true)
    private val tripRepository = mockk<TripRepository>(relaxed = true)
    private val tripDao = mockk<TripDao>(relaxed = true)
    private val idleDrainDao = mockk<IdleDrainDao>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val sessions = mockk<LastSessionRepository>(relaxed = true)

    private suspend fun import(kind: VehicleKind, kwh: Double, km: Double = 6.0): TripEntity {
        val record = BydTripRecord(
            id = 77L, startTimestamp = 1_795_440_000L, endTimestamp = 1_795_441_200L,
            duration = 1200L, tripKm = km, electricityKwh = kwh,
        )
        coEvery { energyDataReader.peekSourceChanged(any()) } returns true
        coEvery { energyDataReader.readTripsSince(any()) } returns listOf(record)
        coEvery { settings.getBatteryCapacity() } returns 31.8
        coEvery { tripDao.getByBydId(77L) } returns null
        coEvery { tripDao.getSameTripByTime(any(), any()) } returns null
        coEvery { sessions.takeMatch(any()) } returns null
        historyImporter(
            energyDataReader = energyDataReader, tripRepository = tripRepository, tripDao = tripDao,
            idleDrainDao = idleDrainDao, settingsRepository = settings, lastSessionRepository = sessions,
            vehicleKind = kindMonitor(kind),
        ).syncFromEnergyData()
        val trip = slot<TripEntity>()
        coVerify(exactly = 1) { tripRepository.insertTrip(capture(trip)) }
        return trip.captured
    }

    @Test fun `hybrid - a trip that gave energy back to the pack keeps its negative kWh`() = runTest {
        val trip = import(VehicleKind.HYBRID, kwh = -0.9)
        assertEquals(-0.9, trip.kwhConsumed!!, 1e-9)
        assertEquals(-15.0, trip.kwhPer100km!!, 1e-9)
    }

    @Test fun `hybrid - more than a full pack back is a broken record`() = runTest {
        val trip = import(VehicleKind.HYBRID, kwh = -40.0)
        assertNull(trip.kwhConsumed)
        assertNull(trip.kwhPer100km)
    }

    @Test fun `hybrid - an ordinary trip is stored as before`() = runTest {
        val trip = import(VehicleKind.HYBRID, kwh = 1.7, km = 9.7)
        assertEquals(1.7, trip.kwhConsumed!!, 1e-9)
    }

    @Test fun `EV - a negative record stays without kWh, as before`() = runTest {
        val trip = import(VehicleKind.EV, kwh = -0.9)
        assertNull(trip.kwhConsumed)
        assertNull(trip.kwhPer100km)
    }
}
