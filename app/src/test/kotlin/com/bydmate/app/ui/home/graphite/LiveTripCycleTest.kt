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
    private val boot = BootInfo(bootCount = 41, bootMs = 1_000L, sleepMs = 0L)
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
        assertTrue(tr.tick(reading(17001.5), t0 + 20_000, boot.copy(bootCount = 42, bootMs = 99_999L)))
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
    }

    @Test
    fun sleepJitterWithinSlackKeepsTheSession() {
        val tr = started()
        assertFalse(tr.tick(reading(17000.0), t0 + 10_000, boot.copy(sleepMs = 4_000L)))
        assertTrue(tr.state.finished.isEmpty())
    }

    @Test
    fun nullBootCountFallsBackToBootTimeWithJitter() {
        val nb = BootInfo(null, 1_000L, 0L)
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.tick(reading(17000.0), t0, nb)
        assertFalse(tr.tick(reading(17000.0), t0 + 10_000, nb.copy(bootMs = 3_000L)))
        assertTrue(tr.state.finished.isEmpty())
        assertTrue(tr.tick(reading(17000.0), t0 + 20_000, nb.copy(bootMs = 31_000L)))
        assertEquals(t0 + 20_000, tr.state.open!!.startMs)
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
            boot = BootInfo(41, 123_456L, 7_000L), sleepAtLastMs = 6_500L, offSinceMs = 99L,
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
}
