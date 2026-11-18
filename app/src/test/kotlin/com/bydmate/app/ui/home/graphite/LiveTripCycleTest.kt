package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One trip per ignition cycle: the head unit's boot, not the author's session, decides. */
class LiveTripCycleTest {
    private val t0 = 10_000_000L
    private val boot = BootInfo(bootCount = 41, elapsedMs = 1_000L, sleepMs = 0L)
    private val min = 60_000L

    private fun reading(odo: Double, gear: Int = 1, speed: Int = 0, ps: Int? = 2, elec: Double? = 2650.0): DiParsData =
        diParsData(mileage = odo, totalElecConsumption = elec).copy(gear = gear, speed = speed, powerState = ps)

    private fun LiveTripTracker.tick(d: DiParsData?, now: Long, b: BootInfo = boot, anchor: Long? = 500L) = onTick(b, anchor, d, now)

    private fun started(): LiveTripTracker =
        LiveTripTracker(LiveTripState(), t0).also { it.tick(reading(17000.0), t0) }

    @Test
    fun s3SameBootAfterTenMinutesInDriveCountsTheGapAsDriving() {
        val tr = started()
        tr.tick(reading(17000.5, 4, 40), t0 + 10_000)
        tr.tick(reading(17005.5, 4, 40), t0 + 10_000 + 10 * min)
        assertTrue(tr.state.finished.isEmpty())
        val o = tr.state.open!!
        assertEquals(t0, o.startMs)
        assertEquals(5.5, o.estimate().km, 1e-9)
        assertEquals(10 * min, o.split.driveMs)
        assertEquals(10_000L, o.split.parkMs)
    }

    @Test
    fun sameBootAfterTenMinutesInParkCountsTheGapAsParked() {
        val tr = started()
        tr.tick(reading(17000.0), t0 + 10_000)
        tr.tick(reading(17000.0), t0 + 10_000 + 10 * min)
        assertTrue(tr.state.finished.isEmpty())
        val o = tr.state.open!!
        assertEquals(0.0, o.estimate().km, 1e-9)
        assertEquals(10 * min + 10_000, o.split.parkMs)
        assertEquals(0L, o.split.driveMs)
    }

    @Test
    fun noFinishOnAFiveMinuteTickGapWithTheSameBoot() {
        val tr = started()
        assertFalse(tr.tick(reading(17000.0), t0 + 5 * min))
        assertNotNull(tr.state.open)
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun differentBootCountFinishesAndOpensNew() {
        val tr = started()
        tr.tick(reading(17001.5, 4, 40), t0 + 10_000)
        assertTrue(tr.tick(reading(17001.5), t0 + 20_000, boot.copy(bootCount = 42)))
        assertEquals(1.5, tr.state.finished.single().km, 1e-9)
        assertEquals(t0 + 10_000, tr.state.finished.single().endMs)
        assertEquals(t0 + 20_000, tr.state.open!!.startMs)
        assertEquals(0.0, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun sleepGrowthOverSlackFinishesAndOpensNew() {
        val tr = started()
        tr.tick(reading(17001.5, 4, 40), t0 + 10_000)
        assertTrue(tr.tick(reading(17001.5), t0 + 20_000, boot.copy(sleepMs = 60_000L)))
        assertEquals(1, tr.state.finished.size)
        assertEquals(boot.copy(sleepMs = 60_000L), tr.state.open!!.boot)
        assertEquals(t0 + 10_000, tr.state.finished.single().endMs)
    }

    @Test
    fun sleepJitterWithinSlackKeepsTheSession() {
        val tr = started()
        assertFalse(tr.tick(reading(17000.0), t0 + 10_000, boot.copy(sleepMs = 4_000L)))
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun nullBootCountWithMonotonicElapsedIsTheSameCycleEvenWhenTheWallClockJumps() {
        val nb = BootInfo(null, 1_000L, 0L)
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(reading(17000.0), t0, nb)
        assertFalse(tr.tick(reading(17000.0), t0 + 2 * 3_600_000L, nb.copy(elapsedMs = 31_000L)))
        assertTrue(tr.state.finished.isEmpty())
        assertEquals(t0, tr.state.open!!.startMs)
    }

    @Test
    fun elapsedGoingBackwardsIsARebootEvenWithNullCount() {
        val nb = BootInfo(null, 100_000L, 0L)
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(reading(17000.0), t0, nb)
        tr.tick(reading(17001.5, 4, 40), t0 + 10_000, nb.copy(elapsedMs = 110_000L))
        assertTrue(tr.tick(reading(17001.5), t0 + 20_000, nb.copy(elapsedMs = 5_000L)))
        assertEquals(1, tr.state.finished.size)
        assertEquals(t0 + 20_000, tr.state.open!!.startMs)
    }

    @Test
    fun gapFallbackAfterElevenMinutesWithoutAnyOtherSignal() {
        val tr = started()
        tr.tick(reading(17001.5, 4, 40), t0 + 10_000, boot.copy(elapsedMs = 11_000L))
        tr.drainEvents()
        assertTrue(tr.tick(reading(17001.5), t0 + 20_000, boot.copy(elapsedMs = 11_000L + 11 * min)))
        val ev = tr.drainEvents()
        assertEquals("gap_fallback", ev.first { it.name == "cycle_end" }.fields.first { it.first == "reason" }.second)
        assertEquals("gap_fallback", ev.first { it.name == "cycle_new" }.fields.first { it.first == "reason" }.second)
        assertEquals(1, tr.state.finished.size)
    }

    @Test
    fun s4GearsInOneSession() {
        val tr = started()
        val gears = listOf(2 to 5, 4 to 40, 3 to 0, 1 to 0)
        gears.forEachIndexed { i, (g, v) -> tr.tick(reading(17000.0 + 0.1 * (i + 1), g, v), t0 + (i + 1) * 10_000L) }
        assertTrue(tr.state.finished.isEmpty())
        assertEquals(t0, tr.state.open!!.startMs)
    }

    @Test
    fun oneOffReadingKeepsTheSession() {
        val tr = started()
        tr.tick(reading(17000.0, ps = 0), t0 + 10_000)
        assertFalse(tr.tick(reading(17000.0, ps = 2), t0 + 20_000))
        assertTrue(tr.state.finished.isEmpty())
        assertNull(tr.state.open!!.offSinceMs)
        assertEquals(t0 + 20_000, tr.state.open!!.lastSeenMs)
    }

    @Test
    fun offForFiveSecondsFinishesAtTheLastOnReading() {
        val tr = started()
        tr.tick(reading(17002.0, 4, 40), t0 + 10_000)
        assertFalse(tr.tick(reading(17002.0, ps = 0), t0 + 20_000))
        assertFalse(tr.tick(reading(17002.0, ps = 0), t0 + 23_000))
        assertTrue(tr.tick(reading(17002.0, ps = 0), t0 + 26_000))
        assertNull(tr.state.open)
        assertEquals(t0 + 10_000, tr.state.finished.single().endMs)
        assertEquals(2.0, tr.state.finished.single().km, 1e-9)
    }

    @Test
    fun onAgainWithoutRebootOpensANewSession() {
        val tr = started()
        tr.tick(reading(17002.0, 4, 40), t0 + 10_000)
        tr.tick(reading(17002.0, ps = 0), t0 + 20_000)
        tr.tick(reading(17002.0, ps = 0), t0 + 30_000)
        assertNull(tr.state.open)
        tr.tick(reading(17002.0, ps = 0), t0 + 40_000)
        assertNull(tr.state.open)
        assertTrue(tr.tick(reading(17002.0, ps = 2), t0 + 50_000))
        assertEquals(t0 + 50_000, tr.state.open!!.startMs)
        assertEquals(1, tr.state.finished.size)
    }

    @Test
    fun nullPowerStateDecidesNothing() {
        val tr = started()
        repeat(5) { tr.tick(reading(17000.0, ps = null), t0 + (it + 1) * 10_000L) }
        assertNotNull(tr.state.open)
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun noSessionOpensWhileTheCarIsOff() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        assertFalse(tr.tick(reading(17000.0, ps = 0), t0))
        assertNull(tr.state.open)
    }

    @Test
    fun anAuthorsAnchorChangeNoLongerEndsTheTrip() {
        val tr = started()
        tr.tick(reading(17001.0), t0 + 10_000, anchor = 900L)
        tr.tick(reading(17002.0), t0 + 20_000, anchor = null)
        assertTrue(tr.state.finished.isEmpty())
        assertEquals(2.0, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun cycleEventsAreReported() {
        val tr = started()
        assertEquals("cycle_new", tr.drainEvents().single().name)
        tr.tick(reading(17001.0), t0 + 2 * min)
        assertEquals("cycle_continue", tr.drainEvents().single().name)
        assertTrue(tr.drainEvents().isEmpty())
    }

    private fun old(lastSeen: Long, anchor: Long = 500L) = LiveTripState(
        open = LiveSession(
            anchorMs = anchor, startMs = lastSeen - 600_000, lastSeenMs = lastSeen,
            base = TripCounters(17000.0, null, null, null), latest = TripCounters(17005.0, null, null, null),
        ),
    )

    @Test
    fun migrationAdoptsWhenTheAuthorsSessionIsTheSame() {
        val tr = LiveTripTracker(old(t0 - 10 * min), t0)
        assertFalse(tr.tick(reading(17005.0), t0, anchor = 500L))
        assertEquals(boot, tr.state.open!!.boot)
        assertEquals(t0 - 10 * min - 600_000, tr.state.open!!.startMs)
        assertTrue(tr.state.finished.isEmpty())
        assertEquals("migrate", tr.drainEvents().first().name)
    }

    @Test
    fun migrationAdoptsAFreshSessionOfAnotherAnchor() {
        val tr = LiveTripTracker(old(t0 - 60_000), t0)
        tr.tick(reading(17005.0), t0, anchor = 900L)
        assertEquals(boot, tr.state.open!!.boot)
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun migrationFinishesAStaleSessionOfAnotherAnchor() {
        val tr = LiveTripTracker(old(t0 - 10 * min), t0)
        assertTrue(tr.tick(reading(17005.0), t0, anchor = 900L))
        assertEquals(5.0, tr.state.finished.single().km, 1e-9)
        assertEquals(t0, tr.state.open!!.startMs)
        assertEquals(boot, tr.state.open!!.boot)
    }

    private val full = LiveTripState(
        open = LiveSession(
            7L, 1L, 2L, TripCounters(17000.0, null, 300.5, null), TripCounters(17012.4, 2652.1, null, 4.0),
            boot = BootInfo(41, 123_456L, 7_000L), sleepAtLastMs = 6_500L, elapsedAtLastMs = 120_000L, offSinceMs = 99L,
        ),
        finished = listOf(TripEstimate(5L, 6L, 12.4, null, 0.6, null)),
    )

    @Test
    fun codecL7RoundTripsTheCycleFields() {
        assertTrue(LiveTripCodec.encode(full).startsWith("L7|"))
        assertEquals(full, LiveTripCodec.decode(LiveTripCodec.encode(full)))
        val noCount = full.copy(open = full.open!!.copy(boot = BootInfo(null, 5L, 6L), offSinceMs = null))
        assertEquals(noCount, LiveTripCodec.decode(LiveTripCodec.encode(noCount)))
        val noBoot = full.copy(open = full.open!!.copy(boot = null))
        assertEquals(noBoot, LiveTripCodec.decode(LiveTripCodec.encode(noBoot)))
        val closed = LiveTripState(closed = ClosedCycle(945, 95_000_000L, 90_000_000L, 1234L))
        assertEquals(closed, LiveTripCodec.decode(LiveTripCodec.encode(closed)))
        val closedNoCount = LiveTripState(closed = ClosedCycle(null, 1L, 2L, 3L))
        assertEquals(closedNoCount, LiveTripCodec.decode(LiveTripCodec.encode(closedNoCount)))
    }

    @Test
    fun codecDecodesL6WithoutBoot() {
        val l6 = "L6|7,1,2,17000.0,,300.5,,17012.4,2652.1,,4.0,0,0,0,0,0,0,,0.0,0.0,,1|-|E"
        val open = LiveTripCodec.decode(l6).open!!
        assertNull(open.boot)
        assertNull(open.offSinceMs)
        assertEquals(7L, open.anchorMs)
        assertTrue(open.rpmSeen)
    }

    // ---- the quickboot head unit: restarts, fresh readings, the closed marker ----

    private val quickbootExit = ExitInfo(10, "stop com.bydmate.app due to quickboot", t0 + 5_000L)
    private val crashExit = ExitInfo(4, "crash", t0 + 5_000L)
    private val day = BootInfo(945, 1_000_000L, 100_000L)

    /** A saved trip of 5 km in a given gear mode whose last tick was [lastSeen] on the clocks of [day]. */
    private fun saved(lastSeen: Long, mode: Mode, closed: ClosedCycle? = null) = LiveTripState(
        open = if (closed != null) null else LiveSession(
            500L, lastSeen - 600_000, lastSeen, TripCounters(17000.0, 2650.0, null, null), TripCounters(17005.0, 2650.0, null, null),
            lastMode = mode, boot = day, sleepAtLastMs = day.sleepMs, elapsedAtLastMs = day.elapsedMs,
        ),
        closed = closed,
    )

    @Test
    fun quickbootRestoreIsANewCycle() {
        val restart = t0 + 3 * 3_600_000L
        val tr = LiveTripTracker(saved(t0, Mode.PARK), restart, quickbootExit)
        val later = day.copy(elapsedMs = day.elapsedMs + 3 * 3_600_000L, sleepMs = day.sleepMs + 3 * 3_600_000L)
        assertTrue(tr.onTick(later, null, null, restart))
        assertEquals(t0, tr.state.finished.single().endMs)
        assertNull(tr.state.open)
        assertTrue(tr.onTick(later, null, reading(17005.0), restart + 20_000))
        assertEquals(restart, tr.state.open!!.startMs)
        assertEquals(0.0, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun quickbootExitAloneSplitsEvenWithoutAnyClockChange() {
        val tr = LiveTripTracker(saved(t0, Mode.PARK), t0 + 10_000, quickbootExit)
        assertTrue(tr.onTick(day, null, null, t0 + 10_000))
        assertEquals("quickboot", tr.drainEvents().first { it.name == "cycle_end" }.fields.first { it.first == "reason" }.second)
    }

    @Test
    fun anOldQuickbootExitBeforeTheLastTickDoesNotSplit() {
        val tr = LiveTripTracker(saved(t0, Mode.PARK), t0 + 10_000, quickbootExit.copy(atMs = t0 - 60_000))
        assertFalse(tr.onTick(day, null, null, t0 + 10_000))
        assertNotNull(tr.state.open)
    }

    @Test
    fun crashRestoreContinuesWithTheGapInDriving() {
        val tr = LiveTripTracker(saved(t0, Mode.PARK), t0 + 40_000, crashExit)
        val now = day.copy(elapsedMs = day.elapsedMs + 40_000L)
        assertFalse(tr.onTick(now, null, null, t0 + 40_000))
        assertFalse(tr.onTick(now, null, reading(17005.4), t0 + 41_000))
        assertTrue(tr.state.finished.isEmpty())
        val sp = tr.state.open!!.split
        assertEquals(41_000L, sp.driveMs)
        assertEquals(0L, sp.parkMs)
        assertEquals(5.4, tr.state.open!!.estimate().km, 1e-9)
    }

    @Test
    fun theFirstNoDataTickAfterARestartDoesNotConsumeTheGap() {
        val tr = LiveTripTracker(saved(t0, Mode.DRIVE), t0 + 9 * min, crashExit)
        val now = day.copy(elapsedMs = day.elapsedMs + 9 * min)
        tr.onTick(now, null, null, t0 + 9 * min)
        tr.onTick(now, null, null, t0 + 9 * min + 10_000)
        assertEquals(t0, tr.state.open!!.lastSeenMs)
        assertEquals(DrivePark(), tr.state.open!!.split)
        tr.onTick(now, null, reading(17005.0), t0 + 9 * min + 20_000, readingAtMs = 1L)
        val sp = tr.state.open!!.split
        assertEquals(9 * min + 20_000, sp.parkMs)
        assertEquals(0L, sp.driveMs)
    }

    @Test
    fun aFreshOffReadingRefedByTheTickerDoesNotConfirmTheOff() {
        val tr = started()
        tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 10_000, readingAtMs = 9_000L)
        for (i in 1..3) assertFalse(tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 10_000 + i * 10_000L, readingAtMs = 9_000L))
        assertNotNull(tr.state.open)
        assertEquals(9_000L, tr.state.open!!.offSinceMs)
        assertEquals(t0, tr.state.open!!.lastSeenMs)
        assertFalse(tr.onTick(boot, null, reading(17000.0, ps = 2), t0 + 50_000, readingAtMs = 40_000L))
        assertTrue(tr.state.finished.isEmpty())
        assertNull(tr.state.open!!.offSinceMs)
    }

    @Test
    fun twoFreshOffReadingsSixSecondsApartConfirmTheOff() {
        val tr = started()
        tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 10_000, readingAtMs = 10_000L)
        assertFalse(tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 13_000, readingAtMs = 13_000L))
        assertTrue(tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 16_000, readingAtMs = 16_000L))
        assertNull(tr.state.open)
        assertNotNull(tr.state.closed)
        assertEquals(t0, tr.drainEvents().first { it.name == "off" }.fields.first { it.first == "lastOn" }.second)
    }

    @Test
    fun aNullPowerStateDoesNotClearAPendingOff() {
        val tr = started()
        tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 10_000, readingAtMs = 10_000L)
        tr.onTick(boot, null, reading(17000.0, ps = null), t0 + 15_000, readingAtMs = 15_000L)
        assertEquals(10_000L, tr.state.open!!.offSinceMs)
        assertTrue(tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 20_000, readingAtMs = 20_000L))
    }

    @Test
    fun restartWhileStillOffOpensNothingUntilANewCycleShows() {
        val tr = started()
        tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 10_000, readingAtMs = 10_000L)
        tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 20_000, readingAtMs = 20_000L)
        val restored = LiveTripPersistence.restore(LiveTripPersistence.encodeOpen(tr.state), LiveTripPersistence.encodeFinished(tr.state), null).state
        assertNotNull(restored.closed)
        val clocks = boot.copy(elapsedMs = boot.elapsedMs + 60_000L)
        val re = LiveTripTracker(restored, t0 + 60_000, crashExit)
        assertFalse(re.onTick(clocks, null, reading(17000.0, ps = null), t0 + 61_000, readingAtMs = 61_000L))
        assertNull(re.state.open)
        assertFalse(re.onTick(clocks, null, reading(17000.0, ps = 0), t0 + 71_000, readingAtMs = 71_000L))
        assertNull(re.state.open)
        // the next ignition: the sleep time grew
        val woke = clocks.copy(elapsedMs = clocks.elapsedMs + 3_600_000L, sleepMs = 3_600_000L)
        assertTrue(re.onTick(woke, null, reading(17000.0, ps = 2), t0 + 3_700_000, readingAtMs = 3_700_000L))
        assertNotNull(re.state.open)
        assertNull(re.state.closed)
    }

    @Test
    fun anOnReadingAfterAnOffInTheSameCycleOpensANewSession() {
        val tr = started()
        tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 10_000, readingAtMs = 10_000L)
        tr.onTick(boot, null, reading(17000.0, ps = 0), t0 + 20_000, readingAtMs = 20_000L)
        assertNull(tr.state.open)
        assertTrue(tr.onTick(boot, null, reading(17000.0, ps = 2), t0 + 30_000, readingAtMs = 30_000L))
        assertNotNull(tr.state.open)
        assertNull(tr.state.closed)
    }
}
