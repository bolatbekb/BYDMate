package com.bydmate.app.fork.hybrid

import com.bydmate.app.data.local.EnergyDataDeadDetector
import com.bydmate.app.data.local.EnergyDataReader
import com.bydmate.app.data.local.dao.LastStateDao
import com.bydmate.app.data.local.dao.TripDao
import com.bydmate.app.data.local.entity.TripEntity
import com.bydmate.app.data.nativestack.VehicleKind
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.diParsData
import com.bydmate.app.data.trips.TripRecorder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A trip recorded by the app itself (no energydata): the pack's net energy from the car's lifetime counter, which falls
 * when regen or the engine puts energy back (Leopard 5 drive log 2026-11-23). A hybrid trip that ends with more energy
 * in the pack keeps a negative figure; an EV trip stays as it was.
 */
class HybridTripRecorderTest {

    private val tripDao = mockk<TripDao>(relaxed = true)
    private val lastState = mockk<LastStateDao>(relaxed = true).also {
        coEvery { it.openTrip(any(), any(), any(), any(), any(), any()) } returns 1
    }
    private var clock = 0L

    private fun recorder(kind: VehicleKind) = TripRecorder(
        tripDao, lastState,
        mockk<EnergyDataReader> { every { isAvailable() } returns false },
        mockk<EnergyDataDeadDetector>(relaxed = true),
        batteryCapacityKwh = { CAPACITY },
        now = { clock },
        kind = { kind },
    )

    /** One reading: odometer, SOC, the lifetime kWh counter, the lifetime fuel counter and the engine km. */
    private data class Car(val km: Double, val soc: Int?, val elec: Double?, val fuel: Double? = null, val hev: Int? = null)

    private fun tick(on: Boolean, c: Car): DiParsData =
        diParsData(powerState = if (on) 2 else 0, mileage = c.km, soc = c.soc, totalElecConsumption = c.elec)
            .copy(fuelTotalL = c.fuel, mileageHevKm = c.hev, engineRpm = if (on) null else 0)

    /** Opens at 0 s on [start], last ON tick at 10 s on [end], then parked until the trip closes. */
    private suspend fun drive(kind: VehicleKind, start: Car, end: Car): TripEntity {
        val rec = recorder(kind)
        for ((ms, on, c) in listOf(
            Triple(0L, true, start), Triple(10_000L, true, end),
            Triple(20_000L, false, end), Triple(80_000L, false, end), Triple(120_000L, false, end),
        )) {
            clock = ms
            rec.consume(tick(on, c))
        }
        val trip = slot<TripEntity>()
        coVerify(exactly = 1) { tripDao.insert(capture(trip)) }
        return trip.captured
    }

    @Test fun `downhill with the engine off - the pack gained energy, the trip shows it with a minus`() = runTest {
        val trip = drive(
            VehicleKind.HYBRID,
            Car(km = 1000.0, soc = 60, elec = 2981.7, fuel = 858.0, hev = 6766),
            Car(km = 1006.0, soc = 63, elec = 2980.8, fuel = 858.0, hev = 6766),
        )
        assertEquals(-0.9, trip.kwhConsumed!!, 1e-6)
        assertEquals(-15.0, trip.kwhPer100km!!, 1e-4)
        assertEquals(0.0, trip.fuelLiters!!, 1e-9)
    }

    @Test fun `the engine charged the pack - a negative kWh next to the litres it burned`() = runTest {
        val trip = drive(
            VehicleKind.HYBRID,
            Car(km = 1000.0, soc = 40, elec = 2981.7, fuel = 858.0, hev = 6766),
            Car(km = 1040.0, soc = 47, elec = 2979.7, fuel = 859.2, hev = 6806),
        )
        assertEquals(-2.0, trip.kwhConsumed!!, 1e-6)
        assertEquals(-5.0, trip.kwhPer100km!!, 1e-4)
        assertEquals(1.2, trip.fuelLiters!!, 1e-9)
        assertEquals(40.0, trip.hevKm!!, 1e-9)
    }

    @Test fun `mixed trip - EV first, then the engine recovers part of it - net kWh drawn`() = runTest {
        val trip = drive(
            VehicleKind.HYBRID,
            Car(km = 1000.0, soc = 70, elec = 2981.7, fuel = 858.0, hev = 6766),
            Car(km = 1030.0, soc = 65, elec = 2983.5, fuel = 858.8, hev = 6776),
        )
        assertEquals(1.8, trip.kwhConsumed!!, 1e-6)
        assertEquals(0.8, trip.fuelLiters!!, 1e-9)
    }

    @Test fun `pure engine trip - counter flat, litres only, no kWh`() = runTest {
        val trip = drive(
            VehicleKind.HYBRID,
            Car(km = 1000.0, soc = 30, elec = 2981.7, fuel = 858.0, hev = 6766),
            Car(km = 1025.0, soc = 30, elec = 2981.7, fuel = 860.0, hev = 6791),
        )
        assertNull(trip.kwhConsumed)
        assertEquals(2.0, trip.fuelLiters!!, 1e-9)
    }

    @Test fun `a counter that fell while the SOC fell is a glitch, not a gain`() = runTest {
        val trip = drive(
            VehicleKind.HYBRID,
            Car(km = 1000.0, soc = 80, elec = 1000.0),
            Car(km = 1010.0, soc = 70, elec = 999.0),
        )
        assertNull(trip.kwhConsumed)
        assertNull(trip.kwhPer100km)
    }

    @Test fun `a counter reset is no gain`() = runTest {
        val trip = drive(
            VehicleKind.HYBRID,
            Car(km = 1000.0, soc = 60, elec = 2981.7),
            Car(km = 1006.0, soc = 61, elec = 0.5),
        )
        assertNull(trip.kwhConsumed)
    }

    @Test fun `a gain far beyond the SOC rise is a glitch`() = runTest {
        val trip = drive(
            VehicleKind.HYBRID,
            Car(km = 1000.0, soc = 60, elec = 2981.7),
            Car(km = 1006.0, soc = 61, elec = 2971.7),
        )
        assertNull("10 kWh back for a 1 % SOC rise", trip.kwhConsumed)
    }

    @Test fun `EV - a descent with the SOC up stays without kWh, as before`() = runTest {
        val trip = drive(
            VehicleKind.EV,
            Car(km = 1000.0, soc = 60, elec = 2981.7),
            Car(km = 1006.0, soc = 63, elec = 2980.8),
        )
        assertNull(trip.kwhConsumed)
        assertNull(trip.kwhPer100km)
    }

    @Test fun `EV - an ordinary trip keeps its counter delta`() = runTest {
        val trip = drive(
            VehicleKind.EV,
            Car(km = 1000.0, soc = 60, elec = 2981.7),
            Car(km = 1010.0, soc = 56, elec = 2983.2),
        )
        assertEquals(1.5, trip.kwhConsumed!!, 1e-6)
    }

    private companion object {
        const val CAPACITY = 31.8
    }
}
