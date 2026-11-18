package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What was driven before the app started, the hybrid rpm rule and standing before the drive. */
class LiveTripCatchUpTest {
    private val t0 = 10_000_000L
    private val boot = BootInfo(bootCount = 41, elapsedMs = 1_000_000L, sleepMs = 0L)
    private val min = 60_000L

    private fun reading(
        odo: Double, gear: Int = 1, speed: Int = 0, ps: Int? = 2, elec: Double = 2650.0,
    ): DiParsData =
        diParsData(mileage = odo, totalElecConsumption = elec).copy(gear = gear, speed = speed, powerState = ps)

    /** A hybrid's reading: it has a tank and the HEV km counter. */
    private fun hybrid(odo: Double, gear: Int = 1, speed: Int = 0, rpm: Int? = null, hev: Int? = null) =
        reading(odo, gear, speed).copy(engineRpm = rpm, fuelTotalL = 120.0, mileageHevKm = hev)

    private fun LiveTripTracker.tick(d: DiParsData?, now: Long, b: BootInfo = boot) = onTick(b, 500L, d, now)

    private fun field(e: TripEvent, key: String) = e.fields.first { it.first == key }.second

    /** A session the quickboot killed at [lastAt]; the new process starts at [startAt] with the sleep grown. */
    private fun afterQuickboot(lastOdo: Double?, lastElec: Double = 2650.0, startAt: Long = t0 + 30 * min): LiveTripTracker {
        val first = LiveTripTracker(LiveTripState(), t0)
        if (lastOdo != null) first.tick(reading(lastOdo, elec = lastElec), t0)
        val restored = LiveTripCodec.decode(LiveTripCodec.encode(first.state))
        val exit = ExitInfo(reasonCode = 10, description = "due to quickboot", atMs = t0 + 1_000)
        return LiveTripTracker(restored, startAt, exit).also { it.tick(null, startAt, boot.copy(elapsedMs = 2_000_000L, sleepMs = 600_000L)) }
    }

    private val later = boot.copy(elapsedMs = 2_060_000L, sleepMs = 600_000L)

    @Test
    fun s2CatchUpFromProcessStartCountsTheUnseenDrive() {
        val startAt = t0 + 30 * min
        val tr = afterQuickboot(17000.0, startAt = startAt)
        tr.drainEvents()
        tr.tick(reading(17000.8, 4, 40, elec = 2650.15), startAt + min, later)
        val o = tr.state.open!!
        assertEquals(startAt, o.startMs)
        assertEquals(0.8, o.estimate().km, 1e-9)
        assertEquals(0.15, o.estimate().kwh!!, 1e-9)
        assertEquals(min, o.split.driveMs)
        assertEquals(0L, o.split.parkMs)
        assertEquals(0.15, o.split.driveKwh, 1e-9)
        val ev = tr.drainEvents().first { it.name == "catch_up" }
        assertEquals(0.8, field(ev, "km") as Double, 1e-9)
        assertEquals(0.15, field(ev, "kwh") as Double, 1e-9)
    }

    @Test
    fun catchUpTooFarIsRejectedAndParksTheInterval() {
        val startAt = t0 + 30 * min
        val tr = afterQuickboot(17000.0, startAt = startAt)
        tr.drainEvents()
        tr.tick(reading(17050.0, 4, 40, elec = 2655.0), startAt + min, later)
        val o = tr.state.open!!
        assertEquals(0.0, o.estimate().km, 1e-9)
        assertEquals(min, o.split.parkMs)
        assertEquals(0L, o.split.driveMs)
        assertEquals(0.0, o.split.parkKwh, 1e-9)
        val ev = tr.drainEvents().first { it.name == "catch_up_rejected" }
        assertEquals("too_far", field(ev, "reason"))
    }

    @Test
    fun catchUpBackwardsIsRejected() {
        val startAt = t0 + 30 * min
        val tr = afterQuickboot(17000.0, startAt = startAt)
        tr.drainEvents()
        tr.tick(reading(16990.0), startAt + min, later)
        assertEquals(0.0, tr.state.open!!.estimate().km, 1e-9)
        assertEquals(min, tr.state.open!!.split.parkMs)
        assertEquals("backwards", field(tr.drainEvents().first { it.name == "catch_up_rejected" }, "reason"))
    }

    @Test
    fun catchUpWithUnknownLastCountersIsRejected() {
        val startAt = t0 + 30 * min
        val tr = afterQuickboot(null, startAt = startAt)
        tr.drainEvents()
        tr.tick(reading(17000.8, 4, 40), startAt + min, later)
        assertEquals(0.0, tr.state.open!!.estimate().km, 1e-9)
        assertEquals(min, tr.state.open!!.split.parkMs)
        assertEquals("unknown", field(tr.drainEvents().first { it.name == "catch_up_rejected" }, "reason"))
    }

    @Test
    fun catchUpInsideThePlausibleWindowAtTheEdge() {
        // 120 s of the first reading's delay + 120 s: 0.5 + 200 * 240 / 3600 = 13.83 km
        val startAt = t0 + 30 * min
        val tr = afterQuickboot(17000.0, startAt = startAt)
        tr.tick(reading(17013.0, 4, 40), startAt + 120_000, later.copy(elapsedMs = 2_120_000L))
        assertEquals(13.0, tr.state.open!!.estimate().km, 1e-9)
        assertEquals(120_000L, tr.state.open!!.split.driveMs)
    }

    @Test
    fun afterAnOffWithoutARebootStartIsTheOnReadingAndCatchUpFromTheOffCounters() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(reading(17002.0, 4, 40), t0)
        tr.tick(reading(17002.0, ps = 0), t0 + 20_000)
        tr.tick(reading(17002.0, ps = 0), t0 + 23_000)
        assertTrue(tr.tick(reading(17002.0, ps = 0), t0 + 26_000))
        assertNull(tr.state.open)
        tr.drainEvents()
        val on = t0 + 5 * min
        tr.tick(reading(17002.3, 2, 3, ps = 2), on)
        val o = tr.state.open!!
        assertEquals(on, o.startMs)
        assertEquals(0.3, o.estimate().km, 1e-9)
        assertEquals(0L, o.split.driveMs + o.split.parkMs)
        assertNotNull(tr.drainEvents().firstOrNull { it.name == "catch_up" })
    }

    @Test
    fun s1StandTenMinutesInParkThenDriveParksTheStandingAndDrives() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        (0..60).forEach { tr.tick(reading(17000.0), t0 + it * 10_000L) }
        (1..10).forEach { tr.tick(reading(17000.0 + 0.1 * it, 4, 30), t0 + 600_000L + it * 10_000L) }
        val o = tr.state.open!!
        assertEquals(1.0, o.estimate().km, 1e-9)
        assertEquals(610_000L, o.split.parkMs) // the interval into the first D tick still belongs to P
        assertEquals(90_000L, o.split.driveMs)
    }

    @Test
    fun s1AfterAProcessStartParksUntilTheFirstReadingToo() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(null, t0)
        (6..60).forEach { tr.tick(reading(17000.0), t0 + it * 10_000L) }
        (1..10).forEach { tr.tick(reading(17000.0 + 0.1 * it, 4, 30), t0 + 600_000L + it * 10_000L) }
        val o = tr.state.open!!
        assertEquals(t0, o.startMs)
        assertEquals(610_000L, o.split.parkMs)
        assertTrue(o.split.driveMs > 0)
        assertEquals(1.0, o.estimate().km, 1e-9)
    }

    @Test
    fun hybridWithTankNullRpmIsEvAndRpmIsIce() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(hybrid(17000.0, rpm = null, hev = 5000), t0)
        assertTrue(tr.state.open!!.rpmSeen)
        tr.tick(hybrid(17000.2, 4, 30, rpm = null, hev = 5000), t0 + 10_000)
        tr.tick(hybrid(17001.0, 4, 60, rpm = 1200, hev = 5001), t0 + 20_000)
        tr.tick(hybrid(17001.5, 4, 60, rpm = 1200, hev = 5001), t0 + 30_000)
        val s = tr.state.open!!
        assertEquals(0.2, s.evKm!!, 1e-9)
        assertEquals(1.3, s.iceKm!!, 1e-9)
        assertEquals(1.3, s.estimate().iceKm!!, 1e-9)
    }

    @Test
    fun hybridFuelPercentOnlyAlsoCountsAsTank() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(reading(17000.0).copy(fuelPercent = 60), t0)
        tr.tick(reading(17000.3, 4, 30).copy(fuelPercent = 60), t0 + 10_000)
        assertEquals(0.3, tr.state.open!!.evKm!!, 1e-9)
    }

    @Test
    fun noTankNullRpmStaysUnknown() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(reading(17000.0).copy(mileageHevKm = 5000), t0)
        tr.tick(reading(17000.5, 4, 30).copy(mileageHevKm = 5003), t0 + 10_000)
        val s = tr.state.open!!
        assertEquals(false, s.rpmSeen)
        assertNull(s.estimate().iceKm)
        assertNull(s.estimate().evKm)
    }

    @Test
    fun catchUpKmAreNotCoveredByTheRpmRule() {
        val startAt = t0 + 30 * min
        val first = LiveTripTracker(LiveTripState(), t0)
        first.tick(hybrid(17000.0, rpm = 0, hev = 5000), t0)
        val exit = ExitInfo(10, "due to quickboot", t0 + 1_000)
        val tr = LiveTripTracker(LiveTripCodec.decode(LiveTripCodec.encode(first.state)), startAt, exit)
        tr.tick(null, startAt, boot.copy(elapsedMs = 2_000_000L, sleepMs = 600_000L))
        tr.tick(hybrid(17000.9, 4, 40, rpm = 0, hev = 5000), startAt + min, later)
        tr.tick(hybrid(17001.0, 4, 40, rpm = 0, hev = 5000), startAt + min + 10_000, later)
        val e = tr.state.open!!.estimate()
        assertEquals(1.0, e.km, 1e-9)
        assertNull(e.iceKm)
        assertNull(e.evKm)
        assertEquals(0.0, e.hevKm!!, 1e-9)
    }

    @Test
    fun lastCountersSurviveTheCodecAndAreConsumedByTheNextSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(reading(17002.0), t0)
        tr.tick(reading(17002.0, ps = 0), t0 + 20_000)
        tr.tick(reading(17002.0, ps = 0), t0 + 26_000)
        val last = tr.state.last!!
        assertEquals(17002.0, last.counters.odometerKm!!, 1e-9)
        assertEquals(t0, last.atMs)
        assertEquals(tr.state.copy(finished = emptyList()), LiveTripCodec.decode(LiveTripPersistence.encodeOpen(tr.state)))
        tr.tick(reading(17002.0), t0 + 120_000)
        assertNull(tr.state.last)
    }

    private fun tankCar(vararg steps: Triple<Double, Double?, Int?>, litres: (Int) -> Double?): LiveTripTracker {
        val tr = LiveTripTracker(LiveTripState(), t0)
        steps.forEachIndexed { i, (odo, fuel, hev) ->
            tr.tick(reading(odo, 4, 40).copy(fuelTotalL = fuel ?: litres(i), fuelPercent = 80, mileageHevKm = hev, engineRpm = null), t0 + i * 120_000L)
        }
        return tr
    }

    @Test
    fun rpmNeverRunningButFuelBurnedIsNotTrusted() {
        val tr = tankCar(Triple(17000.0, 855.7, 6751), Triple(17005.0, 856.5, 6755), Triple(17010.3, 857.3, 6760)) { null }
        val e = tr.state.open!!.estimate()
        assertEquals(9.0, e.hevKm!!, 1e-9)
        assertNull(e.iceKm)
        assertNull(e.evKm)
    }

    @Test
    fun rpmNeverRunningAndNoFuelBurnedIsEv() {
        val tr = tankCar(Triple(17000.0, 855.7, 6751), Triple(17005.0, 855.7, 6755), Triple(17010.3, 855.7, 6760)) { null }
        val e = tr.state.open!!.estimate()
        assertEquals(0.0, e.hevKm!!, 1e-9)
        assertEquals(10.3, e.evKm!!, 1e-9)
    }

    @Test
    fun rpmSeenOnceKeepsTheRuleDespiteFuel() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(hybrid(17000.0, hev = 5000).copy(fuelTotalL = 855.0), t0)
        tr.tick(hybrid(17001.0, 4, 40, rpm = 1500, hev = 5001).copy(fuelTotalL = 855.5), t0 + 120_000)
        tr.tick(hybrid(17005.0, 4, 40, rpm = null, hev = 5005).copy(fuelTotalL = 856.5), t0 + 240_000)
        val s = tr.state.open!!
        assertTrue(s.iceSeen)
        assertTrue(s.rpmTrusted())
        assertEquals(5.0, s.estimate().iceKm!!, 1e-9) // the interval after a running-engine reading still counts as ICE
        assertEquals(0.0, s.estimate().evKm!!, 1e-9)
        assertEquals(s, LiveTripCodec.decode(LiveTripCodec.encode(tr.state)).open)
    }

    @Test
    fun litresUnknownFallsBackToTheHevCounter() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(reading(17000.0).copy(fuelPercent = 80, mileageHevKm = 5000), t0)
        tr.tick(reading(17010.0, 4, 40).copy(fuelPercent = 80, mileageHevKm = 5003), t0 + 150_000)
        val e = tr.state.open!!.estimate()
        assertEquals(3.0, e.hevKm!!, 1e-9)
        assertNull(e.iceKm)
    }

    @Test
    fun catchUpDropsACounterWhoseStepIsImplausibleButKeepsTheOdometer() {
        val startAt = t0 + 30 * min
        val first = LiveTripTracker(LiveTripState(), t0)
        first.tick(reading(17000.0, elec = 2944.0).copy(fuelTotalL = 855.0, mileageHevKm = 5000), t0)
        val exit = ExitInfo(10, "due to quickboot", t0 + 1_000)
        val tr = LiveTripTracker(LiveTripCodec.decode(LiveTripCodec.encode(first.state)), startAt, exit)
        tr.tick(null, startAt, boot.copy(elapsedMs = 2_000_000L, sleepMs = 600_000L))
        tr.drainEvents()
        // overnight-like kWh drop of 14, a refuel-like fuel jump of 40 L, HEV counter jumped far
        tr.tick(reading(17000.4, elec = 2930.0).copy(fuelTotalL = 895.0, mileageHevKm = 5200), startAt + 30_000, later)
        val o = tr.state.open!!
        assertEquals(17000.0, o.base.odometerKm!!, 1e-9)
        assertEquals(2930.0, o.base.elecKwh!!, 1e-9)
        assertEquals(895.0, o.base.fuelL!!, 1e-9)
        assertEquals(5200.0, o.base.hevKm!!, 1e-9)
        assertEquals(0.4, o.estimate().km, 1e-9)
        assertEquals(0.0, o.split.driveKwh, 1e-9)
        assertEquals("kwh+l+hev", field(tr.drainEvents().first { it.name == "catch_up" }, "rejected"))
    }

    /** A catch-up 30 s after the process start with the kWh counter [elec] on the first reading; the rejected list. */
    private fun rejectedFor(elec: Double): Any? {
        val startAt = t0 + 30 * min
        val tr = afterQuickboot(17000.0, lastElec = 2650.0, startAt = startAt)
        tr.drainEvents()
        tr.tick(reading(17000.4, 4, 40, elec = elec), startAt + 30_000, later)
        return field(tr.drainEvents().first { it.name == "catch_up" }, "rejected")
    }

    @Test
    fun catchUpKwhNegativeBoundIsOnePlusSixtyKwPerHour() {
        // 30 s + 120 s slack = 0.0417 h: the drop limit is 1 + 60 * 0.0417 = 3.5 kWh
        assertNull(rejectedFor(2650.0 - 3.4))
        assertEquals("kwh", rejectedFor(2650.0 - 3.6))
        assertEquals("kwh", rejectedFor(2650.0 - 14.0))
    }

    @Test
    fun catchUpKwhPositiveBoundIsOnePlusThreeHundredKwPerHour() {
        // the same 0.0417 h: the rise limit is 1 + 300 * 0.0417 = 13.5 kWh
        assertNull(rejectedFor(2650.0 + 13.0))
        assertEquals("kwh", rejectedFor(2650.0 + 14.0))
    }

    @Test
    fun catchUpKeepsPlausibleCountersAsBase() {
        val startAt = t0 + 30 * min
        val first = LiveTripTracker(LiveTripState(), t0)
        first.tick(reading(17000.0, elec = 2650.0).copy(fuelTotalL = 855.0, mileageHevKm = 5000), t0)
        val tr = LiveTripTracker(LiveTripCodec.decode(LiveTripCodec.encode(first.state)), startAt, ExitInfo(10, "due to quickboot", t0 + 1_000))
        tr.tick(null, startAt, boot.copy(elapsedMs = 2_000_000L, sleepMs = 600_000L))
        tr.tick(reading(17000.8, 4, 40, elec = 2650.2).copy(fuelTotalL = 855.1, mileageHevKm = 5001), startAt + min, later)
        val o = tr.state.open!!
        assertEquals(2650.0, o.base.elecKwh!!, 1e-9)
        assertEquals(855.0, o.base.fuelL!!, 1e-9)
        assertEquals(5000.0, o.base.hevKm!!, 1e-9)
    }
}
