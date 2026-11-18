package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTripTrackerTest {

    private fun reading(
        odo: Double?, elec: Double? = null, fuel: Double? = null, hev: Double? = null,
    ): DiParsData = diParsData(mileage = odo, totalElecConsumption = elec)
        .copy(fuelTotalL = fuel, mileageHevKm = hev?.toInt())

    private val t0 = 1_000_000L
    private val anchorA = 500L
    private val anchorB = 900L

    private fun tick(tr: LiveTripTracker, anchor: Long?, odo: Double?, elec: Double? = null, now: Long) =
        tr.onTick(anchor, reading(odo, elec), now)

    @Test
    fun firstTickWithAnchorOpensSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        assertTrue(tick(tr, anchorA, 17000.0, 2650.0, t0))
        assertEquals(t0, tr.state.open!!.startMs)
        assertEquals(anchorA, tr.state.open!!.anchorMs)
    }

    @Test
    fun deltasGrowWithCounters() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onTick(anchorA, reading(17000.0, 2650.0, 300.0, 5000.0), t0)
        for (i in 1..14) tr.onTick(anchorA, reading(17000.0, 2650.0, 300.0, 5000.0), t0 + i * 10_000L)
        tr.onTick(anchorA, reading(17012.4, 2652.1, 300.6, 5004.0), t0 + 150_000) // 12.4 km: under 300 km/h
        val e = tr.state.open!!.estimate()
        assertEquals(12.4, e.km, 1e-9)
        assertEquals(2.1, e.kwh!!, 1e-9)
        assertEquals(0.6, e.litres!!, 1e-9)
        assertEquals(4.0, e.hevKm!!, 1e-9) // no rpm ever seen: the counter delta stands
    }

    @Test
    fun lateCounterTakesFirstNonNullAsBase() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        tick(tr, anchorA, 17001.0, 2650.0, t0 + 10_000)
        tick(tr, anchorA, 17002.0, 2651.0, t0 + 20_000)
        val e = tr.state.open!!.estimate()
        assertEquals(1.0, e.kwh!!, 1e-9)
        assertNull(e.litres)
    }

    @Test
    fun standstillWithSameReadingsKeepsTrip() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        var now = t0
        repeat(30) { now += 10_000; tick(tr, anchorA, 17000.0 + (it + 1) / 3.0, null, now) }
        tick(tr, anchorA, 17010.0, null, now)
        repeat(180) { now += 10_000; tick(tr, anchorA, 17010.0, null, now) }
        val open = tr.state.open!!
        assertEquals(t0, open.startMs)
        assertEquals(10.0, open.estimate().km, 1e-9)
        assertEquals(now, open.estimate().endMs)
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun anchorNullAfterSeenFinishes() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        tick(tr, anchorA, 17003.0, null, t0 + 60_000)
        assertTrue(tick(tr, null, 17003.0, null, t0 + 70_000))
        assertNull(tr.state.open)
        assertEquals(3.0, tr.state.finished.last().km, 1e-9)
    }

    @Test
    fun anchorChangeFinishesAndOpensNew() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        tick(tr, anchorA, 17002.0, null, t0 + 60_000)
        assertTrue(tick(tr, anchorB, 17002.0, null, t0 + 70_000))
        assertEquals(1, tr.state.finished.size)
        assertEquals(2.0, tr.state.finished.last().km, 1e-9)
        assertEquals(anchorB, tr.state.open!!.anchorMs)
        assertEquals(0.0, tr.state.open!!.estimate().km, 1e-9)
    }

    private fun restored(lastSeen: Long, anchor: Long = anchorA) = LiveTripState(
        open = LiveSession(
            anchorMs = anchor, startMs = lastSeen - 600_000, lastSeenMs = lastSeen,
            base = TripCounters(17000.0, null, null, null), latest = TripCounters(17005.0, null, null, null),
        ),
    )

    @Test
    fun firstAnchorAfterRestoreIsAdopted() {
        val tr = LiveTripTracker(restored(t0), t0 + 20_000)
        assertFalse(tick(tr, anchorB, 17005.0, null, t0 + 30_000))
        assertEquals(anchorB, tr.state.open!!.anchorMs)
        assertEquals(5.0, tr.state.open!!.estimate().km, 1e-9)
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun nullAnchorAtStartupKeepsRestoredTrip() {
        val tr = LiveTripTracker(restored(t0), t0 + 5_000)
        tick(tr, null, 17005.0, null, t0 + 10_000)
        assertNotNull(tr.state.open)
        tick(tr, null, 17005.0, null, t0 + 60_000)
        assertNotNull(tr.state.open)
        assertTrue(tick(tr, null, 17005.0, null, t0 + 130_000))
        assertNull(tr.state.open)
        assertEquals(5.0, tr.state.finished.last().km, 1e-9)
    }

    @Test
    fun gapFinishesEvenWithSameAnchor() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        tick(tr, anchorA, 17002.0, null, t0 + 60_000)
        val t = t0 + 60_000
        assertTrue(tick(tr, anchorA, 17002.0, null, t + 3 * 60_000))
        assertEquals(2.0, tr.state.finished.last().km, 1e-9)
        assertEquals(t + 180_000, tr.state.open!!.startMs)
    }

    @Test
    fun odometerBackwardsRebases() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        tick(tr, anchorA, 17005.0, null, t0 + 60_000)
        tick(tr, anchorA, 16000.0, null, t0 + 70_000)
        tick(tr, anchorA, 16001.0, null, t0 + 80_000)
        assertEquals(t0, tr.state.open!!.startMs)
        assertEquals(6.0, tr.state.open!!.estimate().km, 1e-9)
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun odometerJumpRebases() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        tick(tr, anchorA, 17001.0, null, t0 + 30_000)
        tick(tr, anchorA, 18000.0, null, t0 + 40_000)
        tick(tr, anchorA, 18002.0, null, t0 + 100_000)
        assertEquals(3.0, tr.state.open!!.estimate().km, 1e-9)
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun outageWithSameReadingThenFreshReadingIsNotAJump() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        for (i in 1..9) tick(tr, anchorA, 17000.0, null, t0 + i * 10_000L)
        tick(tr, anchorA, 17003.0, null, t0 + 100_000)
        assertEquals(3.0, tr.state.open!!.estimate().km, 1e-9)
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun adoptedTripKeepsCountingThenNewAnchorFinishes() {
        val tr = LiveTripTracker(restored(t0), t0 + 20_000)
        tick(tr, anchorB, 17005.0, null, t0 + 30_000)
        tick(tr, anchorB, 17008.0, null, t0 + 60_000)
        assertEquals(8.0, tr.state.open!!.estimate().km, 1e-9)
        assertTrue(tick(tr, 1300L, 17008.0, null, t0 + 70_000))
        assertEquals(8.0, tr.state.finished.last().km, 1e-9)
        assertEquals(1300L, tr.state.open!!.anchorMs)
        assertEquals(0.0, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun dataNullStillTicks() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        tick(tr, anchorA, 17002.0, null, t0 + 10_000)
        tr.onTick(anchorA, null, t0 + 60_000)
        assertEquals(t0 + 60_000, tr.state.open!!.estimate().endMs)
        assertEquals(2.0, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun regenDipDoesNotSplitSession() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, 2650.0, t0)
        tick(tr, anchorA, 17001.0, 2651.0, t0 + 10_000)
        tick(tr, anchorA, 17002.0, 2650.8, t0 + 20_000)
        assertEquals(t0, tr.state.open!!.startMs)
        assertEquals(0.8, tr.state.open!!.estimate().kwh!!, 1e-9)
    }

    @Test
    fun finishedExpiresAfterTtl() {
        val prev = TripEstimate(1L, t0, 12.4, null, null, null)
        val tr = LiveTripTracker(LiveTripState(finished = listOf(prev)), t0 + 1)
        assertEquals(listOf(prev), tr.state.finished)
        tick(tr, null, 17000.0, null, t0 + LiveTripTracker.FINISHED_TTL_MS + 1)
        assertTrue(tr.state.finished.isEmpty())
        assertTrue(LiveTripTracker(LiveTripState(finished = listOf(prev)), t0 + LiveTripTracker.FINISHED_TTL_MS + 1).state.finished.isEmpty())
    }

    private val sample = LiveTripState(
        open = LiveSession(
            7L, 1L, 2L, TripCounters(17000.0, null, 300.5, null), TripCounters(17012.4, 2652.1, null, 4.0),
        ),
        finished = listOf(TripEstimate(5L, 6L, 12.4, null, 0.6, null)),
    )

    @Test
    fun codecRoundTrip() {
        assertEquals(sample, LiveTripCodec.decode(LiveTripCodec.encode(sample)))
        assertEquals(LiveTripState(), LiveTripCodec.decode(LiveTripCodec.encode(LiveTripState())))
    }

    @Test
    fun codecWritesL6AndReadsL2() {
        assertTrue(LiveTripCodec.encode(sample).startsWith("L6|"))
        val open = LiveTripCodec.decode("L2|5,6,7,17000.0,,,,17001.0,,,|-|E").open!!
        assertEquals(5L, open.anchorMs)
        assertEquals(5L, open.startMs)
        assertEquals(7L, open.lastSeenMs)
        assertEquals(1.0, open.estimate().km, 1e-9)
    }

    @Test
    fun codecSurvivesGarbage() {
        val empty = LiveTripState()
        assertEquals(empty, LiveTripCodec.decode(null))
        assertEquals(empty, LiveTripCodec.decode(""))
        assertEquals(empty, LiveTripCodec.decode("x;y"))
        assertEquals(empty, LiveTripCodec.decode(LiveTripCodec.encode(sample).dropLast(3)))
    }

    @Test
    fun slowReadingAfterLongDriveIsNotAJump() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        // 20 km in 100 s would be a jump; 20 km in 4.5 min = 267 km/h is within the 300 km/h limit
        tick(tr, anchorA, 17000.0, null, t0 + 100_000)
        tick(tr, anchorA, 17000.0, null, t0 + 200_000)
        tick(tr, anchorA, 17020.0, null, t0 + 270_000)
        assertEquals(20.0, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun implausibleKwhIsNull() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, 2650.0, t0)
        tick(tr, anchorA, 17001.0, 56950.0, t0 + 10_000)
        assertNull(tr.state.open!!.estimate().kwh)
        assertEquals(1.0, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun implausibleFinishedIsDropped() {
        val prev = TripEstimate(1L, 2L, 12.4, null, null, null)
        val open = LiveSession(
            anchorA, t0 - 600_000, t0, TripCounters(17000.0, null, null, null), TripCounters(18600.0, null, null, null),
        )
        val tr = LiveTripTracker(LiveTripState(open, listOf(prev)), t0 + 400_000)
        tick(tr, null, null, null, t0 + 400_000)
        assertNull(tr.state.open)
        assertEquals(listOf(prev), tr.state.finished)
    }

    private fun drive(tr: LiveTripTracker, anchor: Long, from: Double, km: Double, start: Long): Long {
        tick(tr, anchor, from, null, start)
        tick(tr, anchor, from + km, null, start + 10_000)
        tick(tr, null, from + km, null, start + 20_000)
        return start + 30_000
    }

    @Test
    fun twoDrivesBothPending() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        var now = drive(tr, anchorA, 17000.0, 1.5, t0)
        now = drive(tr, anchorB, 17005.0, 1.5, now)
        assertEquals(2, tr.state.finished.size)
    }

    @Test
    fun finishedListCapsAtMax() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        var now = t0
        val n = LiveTripTracker.MAX_FINISHED + 1
        repeat(n) { i -> now = drive(tr, anchorA + i, 17000.0 + i * 5, 1.1 + (i % 10) / 10.0, now) }
        assertEquals(LiveTripTracker.MAX_FINISHED, tr.state.finished.size)
    }

    @Test
    fun codecReadsL1() {
        val s = LiveTripCodec.decode("L1|-|5,6,12.4,,0.6,|E")
        assertEquals(listOf(TripEstimate(5L, 6L, 12.4, null, 0.6, null)), s.finished)
        assertNull(s.open)
    }

    @Test
    fun singleTenthStepCountsAsMoved() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        drive(tr, anchorA, 17000.0, 0.1, t0)
        assertEquals(1, tr.state.finished.size)
    }

    @Test
    fun countersOfReadsStartupZeroAsNull() {
        val c = TripCounters.of(reading(0.5, 0.0, 0.0, 0.0))
        assertNull(c.odometerKm)
        assertNull(c.elecKwh)
        assertNull(c.fuelL)
        assertNull(c.hevKm)
    }

    @Test
    fun missingOdometerDoesNotLookLikeJump() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tick(tr, anchorA, 17000.0, null, t0)
        for (i in 1..9) tick(tr, anchorA, null, null, t0 + i * 10_000L)
        tick(tr, anchorA, 17005.0, null, t0 + 100_000)
        assertTrue(tr.state.finished.isEmpty())
        assertEquals(t0, tr.state.open!!.startMs)
        assertEquals(5.0, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun codecRoundTripSeveralFinished() {
        val s = LiveTripState(
            finished = listOf(
                TripEstimate(1L, 2L, 3.5, 0.4, null, 1.0), TripEstimate(5L, 6L, 12.4, null, 0.6, null),
            ),
        )
        assertEquals(s, LiveTripCodec.decode(LiveTripCodec.encode(s)))
    }

    private fun gearTick(tr: LiveTripTracker, gear: Int?, elec: Double?, now: Long, fuel: Double? = null) =
        tr.onTick(anchorA, reading(17000.0, elec, fuel).copy(gear = gear, speed = if (gear == 4) 40 else 0), now)

    @Test
    fun modeOfCodes() {
        assertEquals(Mode.PARK, modeOf(1, 0))
        assertEquals(Mode.DRIVE, modeOf(2, 0))
        assertEquals(Mode.PARK, modeOf(3, 50))
        assertEquals(Mode.DRIVE, modeOf(4, 0))
        assertEquals(Mode.PARK, modeOf(null, 0))
        assertEquals(Mode.PARK, modeOf(null, null))
        assertEquals(Mode.DRIVE, modeOf(null, 30))
    }

    @Test
    fun modeOfOdometerFallbackOnlyWithoutGearAndSpeed() {
        assertEquals(Mode.DRIVE, modeOf(null, null, 0.05))
        assertEquals(Mode.PARK, modeOf(null, null, 0.04))
        assertEquals(Mode.PARK, modeOf(null, null, null))
        assertEquals(Mode.PARK, modeOf(null, 0, 1.0))
        assertEquals(Mode.PARK, modeOf(1, null, 1.0))
    }

    @Test
    fun nullGearAndSpeedUseOdometerMovement() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        fun blind(odo: Double, now: Long) =
            tr.onTick(anchorA, reading(odo, 2650.0).copy(gear = null, speed = null), now)
        blind(17000.0, t0)
        blind(17000.0, t0 + 10_000) // not moved: PARK
        blind(17000.2, t0 + 20_000) // moved: DRIVE from the next interval on
        blind(17000.4, t0 + 30_000)
        val sp = tr.state.open!!.split
        assertEquals(20_000L, sp.parkMs)
        assertEquals(10_000L, sp.driveMs)
    }

    @Test
    fun parkStopCountsToPark() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        gearTick(tr, 1, 2650.0, t0)
        for (i in 1..60) gearTick(tr, 1, 2650.0 + 0.25 * i / 60, t0 + i * 10_000L)
        val sp = tr.state.open!!.estimate().split!!
        assertEquals(600_000L, sp.parkMs)
        assertEquals(0.25, sp.parkKwh, 1e-9)
        assertEquals(0L, sp.driveMs)
        assertEquals(0.0, sp.driveKwh, 0.0)
    }

    @Test
    fun intervalGoesToTheEarlierMode() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        gearTick(tr, 4, 2650.0, t0, 300.0)
        gearTick(tr, 1, 2650.3, t0 + 10_000, 300.1)
        gearTick(tr, 1, 2650.4, t0 + 20_000, 300.1)
        val sp = tr.state.open!!.estimate().split!!
        assertEquals(10_000L, sp.driveMs)
        assertEquals(10_000L, sp.parkMs)
        assertEquals(0.3, sp.driveKwh, 1e-9)
        assertEquals(0.1, sp.driveL, 1e-9)
        assertEquals(0.1, sp.parkKwh, 1e-9)
    }

    @Test
    fun regenIntervalSumsNegative() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        gearTick(tr, 4, 2650.0, t0)
        gearTick(tr, 4, 2650.5, t0 + 10_000)
        gearTick(tr, 4, 2650.3, t0 + 20_000)
        assertEquals(0.3, tr.state.open!!.split.driveKwh, 1e-9)
    }

    @Test
    fun tickerBeatGoesToLastMode() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        gearTick(tr, 1, 2650.0, t0)
        tr.onTick(anchorA, null, t0 + 10_000)
        assertEquals(10_000L, tr.state.open!!.split.parkMs)
        assertEquals(Mode.PARK, tr.state.open!!.lastMode)
    }

    @Test
    fun gapIsNotAttributed() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onTick(anchorA, reading(17000.0, 2650.0).copy(gear = 4, speed = 40), t0)
        tr.onTick(anchorA, reading(17000.5, 2650.1).copy(gear = 4, speed = 40), t0 + 10_000)
        tr.onTick(anchorA, reading(17001.0, 2650.2).copy(gear = 4, speed = 40), t0 + 10_000 + LiveTripTracker.GAP_MS + 1)
        assertEquals(10_000L, tr.state.finished.single().split!!.driveMs)
        assertEquals(0L, tr.state.open!!.split.driveMs)
    }

    @Test
    fun implausibleEnergyJumpAddsOnlyTime() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        gearTick(tr, 4, 2650.0, t0, 300.0)
        gearTick(tr, 4, 2850.0, t0 + 10_000, 300.0)
        gearTick(tr, 4, 3.0, t0 + 20_000, 5000.0)
        val sp = tr.state.open!!.split
        assertEquals(20_000L, sp.driveMs)
        assertEquals(0.0, sp.driveKwh, 0.0)
        assertEquals(0.0, sp.driveL, 0.0)
    }

    @Test
    fun normalTenMinuteParkStepCounts() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        gearTick(tr, 1, 2650.0, t0)
        for (i in 1..60) gearTick(tr, 1, 2650.0 + 0.25 * i / 60, t0 + i * 10_000L)
        assertEquals(0.25, tr.state.open!!.split.parkKwh, 1e-9)
    }

    @Test
    fun restoreFromL4KeepsSplitAndMode() {
        val a = LiveTripTracker(LiveTripState(), t0)
        gearTick(a, 1, 2650.0, t0)
        gearTick(a, 1, 2650.1, t0 + 10_000)
        val b = LiveTripTracker(LiveTripCodec.decode(LiveTripCodec.encode(a.state)), t0 + 10_000)
        gearTick(b, 1, 2650.2, t0 + 20_000)
        val sp = b.state.open!!.split
        assertEquals(20_000L, sp.parkMs)
        assertEquals(0.2, sp.parkKwh, 1e-9)
    }

    @Test
    fun anchorChangeStartsZeroSplit() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        gearTick(tr, 1, 2650.0, t0)
        gearTick(tr, 1, 2650.1, t0 + 10_000)
        tr.onTick(anchorB, reading(17000.0, 2650.1).copy(gear = 1, speed = 0), t0 + 20_000)
        assertEquals(DrivePark(), tr.state.open!!.split)
    }

    @Test
    fun odometerRebaseDoesNotTouchEnergySplit() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onTick(anchorA, reading(17000.0, 2650.0).copy(gear = 4, speed = 40), t0)
        tr.onTick(anchorA, reading(16000.0, 2650.1).copy(gear = 4, speed = 40), t0 + 10_000)
        assertEquals(0.1, tr.state.open!!.split.driveKwh, 1e-9)
    }

    @Test
    fun codecRoundTripWithSplit() {
        val s = LiveTripState(
            open = sample.open!!.copy(split = DrivePark(1, 2, 0.5, -0.25, 0.1, 0.0), lastMode = Mode.PARK),
            finished = listOf(TripEstimate(5L, 6L, 12.4, 1.0, 0.6, null, DrivePark(10, 20, 0.5, 0.5, 0.0, 0.0))),
        )
        assertEquals(s, LiveTripCodec.decode(LiveTripCodec.encode(s)))
    }

    @Test
    fun oldVersionsDecodeWithoutSplit() {
        val l3 = LiveTripCodec.decode("L3|5,6,7,17000.0,,,,17001.0,,,|5,6,12.4,,0.6,|E")
        assertEquals(DrivePark(), l3.open!!.split)
        assertNull(l3.open!!.lastMode)
        assertNull(l3.finished.single().split)
        assertNull(LiveTripCodec.decode("L1|-|5,6,12.4,,0.6,|E").finished.single().split)
    }
}
