package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.remote.diParsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTripEvIceTest {
    private val t0 = 1_000_000L
    private val anchor = 500L

    private fun reading(odo: Double?, rpm: Int?): DiParsData =
        diParsData(mileage = odo, totalElecConsumption = 2650.0).copy(engineRpm = rpm)

    private fun tracker(vararg steps: Pair<Double, Int?>): LiveTripTracker {
        val tr = LiveTripTracker(LiveTripState(), t0)
        steps.forEachIndexed { i, (odo, rpm) -> tr.onTick(anchor, reading(odo, rpm), t0 + i * 10_000L) }
        return tr
    }

    @Test
    fun rpmAtEndCountsIce() {
        val s = tracker(17000.0 to 0, 17000.2 to 1500).state.open!!
        assertEquals(0.2, s.iceKm!!, 1e-9)
        assertEquals(0.0, s.evKm!!, 1e-9)
    }

    @Test
    fun rpmAtStartCountsIce() {
        val s = tracker(17000.0 to 1500, 17000.2 to 0).state.open!!
        assertEquals(0.2, s.iceKm!!, 1e-9)
        assertEquals(0.0, s.evKm!!, 1e-9)
    }

    @Test
    fun rpmZeroCountsEv() {
        val s = tracker(17000.0 to 0, 17000.2 to 0).state.open!!
        assertEquals(0.0, s.iceKm!!, 1e-9)
        assertEquals(0.2, s.evKm!!, 1e-9)
    }

    @Test
    fun rpmNullCountsEv() {
        val s = tracker(17000.0 to null, 17000.2 to null).state.open!!
        assertEquals(0.2, s.evKm!!, 1e-9)
        assertEquals(0.0, s.iceKm!!, 1e-9)
    }

    @Test
    fun mixedIntervalsAddUp() {
        val s = tracker(17000.0 to 0, 17000.2 to 0, 17000.5 to 900, 17000.9 to 900, 17001.0 to 0).state.open!!
        assertEquals(0.2 + 0.0, s.evKm!!, 1e-9)
        assertEquals(0.3 + 0.4 + 0.1, s.iceKm!!, 1e-9)
    }

    @Test
    fun backwardsStepCountsNowhere() {
        val s = tracker(17000.0 to 1000, 16000.0 to 1000).state.open!!
        assertEquals(0.0, s.iceKm!!, 1e-9)
        assertEquals(0.0, s.evKm!!, 1e-9)
    }

    @Test
    fun jumpStepCountsNowhere() {
        val s = tracker(17000.0 to 1000, 17100.0 to 1000).state.open!!
        assertEquals(0.0, s.iceKm!!, 1e-9)
        assertEquals(0.0, s.evKm!!, 1e-9)
    }

    @Test
    fun tickerBeatWithoutDataKeepsRpmAndKm() {
        val tr = tracker(17000.0 to 1000)
        tr.onTick(anchor, null, t0 + 10_000)
        tr.onTick(anchor, reading(17000.2, 0), t0 + 20_000)
        assertEquals(0.2, tr.state.open!!.iceKm!!, 1e-9)
    }

    @Test
    fun estimateHevKmIsIceKm() {
        val tr = LiveTripTracker(LiveTripState(), t0)
        tr.onTick(anchor, reading(17000.0, 0).copy(mileageHevKm = 5000), t0)
        tr.onTick(anchor, reading(17000.2, 1200).copy(mileageHevKm = 5003), t0 + 10_000)
        val e = tr.state.open!!.estimate()
        assertEquals(0.2, e.hevKm!!, 1e-9)
        assertEquals(0.2, e.iceKm!!, 1e-9)
        assertEquals(0.0, e.evKm!!, 1e-9)
    }

    @Test
    fun unknownIceKmFallsBackToCounterDelta() {
        val s = LiveSession(
            1L, 1L, 2L, TripCounters(17000.0, null, null, 5000.0), TripCounters(17001.0, null, null, 5004.0),
            iceKm = null, evKm = null,
        )
        val e = s.estimate()
        assertEquals(4.0, e.hevKm!!, 1e-9)
        assertNull(e.iceKm)
        assertNull(e.evKm)
    }

    @Test
    fun finishedKeepsIceAndEv() {
        val tr = tracker(17000.0 to 0, 17000.1 to 800, 17000.2 to 0)
        tr.onTick(null, null, t0 + 40_000)
        val f = tr.state.finished.single()
        assertEquals(0.2, f.iceKm!!, 1e-9)
        assertEquals(0.0, f.evKm!!, 1e-9)
    }

    @Test
    fun ttlIs31Days() {
        assertEquals(31 * 24 * 3_600_000L, LiveTripTracker.FINISHED_TTL_MS)
        val prev = TripEstimate(1L, t0, 12.4, null, null, null)
        assertEquals(1, LiveTripTracker(LiveTripState(finished = listOf(prev)), t0 + LiveTripTracker.FINISHED_TTL_MS).state.finished.size)
        assertTrue(LiveTripTracker(LiveTripState(finished = listOf(prev)), t0 + LiveTripTracker.FINISHED_TTL_MS + 1).state.finished.isEmpty())
    }

    @Test
    fun finishedCapIs400() {
        assertEquals(400, LiveTripTracker.MAX_FINISHED)
        val many = (1..450).map { TripEstimate(it.toLong(), t0, 1.0, null, null, null) }
        val decoded = LiveTripCodec.decode(LiveTripCodec.encode(LiveTripState(finished = many)))
        assertEquals(400, decoded.finished.size)
        assertEquals(51L, decoded.finished.first().startMs)
    }

    @Test
    fun codecRoundTripWithIceEv() {
        val s = LiveTripState(
            open = LiveSession(7L, 1L, 2L, TripCounters(17000.0, null, null, null), TripCounters(17001.0, null, null, null),
                iceKm = 0.4, evKm = 0.6, lastRpm = 900),
            finished = listOf(TripEstimate(5L, 6L, 12.4, 1.0, 0.6, 3.0, null, 3.0, 9.4)),
        )
        assertTrue(LiveTripCodec.encode(s).startsWith("L5|"))
        assertEquals(s, LiveTripCodec.decode(LiveTripCodec.encode(s)))
    }

    @Test
    fun oldCodecDecodesWithNullIceEv() {
        val l4 = LiveTripCodec.decode(
            "L4|7,1,2,17000.0,,,,17001.0,,,,1,2,0.5,0.0,0.1,0.0,DRIVE|5,6,12.4,,0.6,,10,20,0.5,0.5,0.0,0.0|E",
        )
        assertNull(l4.open!!.iceKm)
        assertNull(l4.open!!.evKm)
        assertNull(l4.open!!.lastRpm)
        assertEquals(Mode.DRIVE, l4.open!!.lastMode)
        val f = l4.finished.single()
        assertNull(f.iceKm)
        assertNull(f.evKm)
        assertEquals(10L, f.split!!.driveMs)
        val l3 = LiveTripCodec.decode("L3|5,6,7,17000.0,,,,17001.0,,,|5,6,12.4,,0.6,|E")
        assertNull(l3.open!!.iceKm)
        assertNull(l3.finished.single().evKm)
    }

    // --- storage split ---

    private val open = LiveSession(7L, 1L, 2L, TripCounters(17000.0, null, null, null), TripCounters(17001.0, null, null, null))
    private val fin = listOf(TripEstimate(5L, 6L, 12.4, 1.0, 0.6, 3.0, null, 3.0, 9.4))

    @Test
    fun openKeyHoldsNoFinishedList() {
        val raw = LiveTripPersistence.encodeOpen(LiveTripState(open = open, finished = fin))
        val back = LiveTripCodec.decode(raw)
        assertEquals(open, back.open)
        assertTrue(back.finished.isEmpty())
    }

    @Test
    fun finishedKeyHoldsNoOpenSession() {
        val back = LiveTripCodec.decode(LiveTripPersistence.encodeFinished(LiveTripState(open = open, finished = fin)))
        assertNull(back.open)
        assertEquals(fin, back.finished)
    }

    @Test
    fun finishedIsSavedOnlyWhenChanged() {
        assertFalse(LiveTripPersistence.shouldSaveFinished(fin, fin.toList()))
        assertTrue(LiveTripPersistence.shouldSaveFinished(fin, emptyList()))
        assertTrue(LiveTripPersistence.shouldSaveFinished(emptyList(), fin))
    }

    @Test
    fun restoreJoinsSeparateKeys() {
        val r = LiveTripPersistence.restore(
            LiveTripPersistence.encodeOpen(LiveTripState(open = open)),
            LiveTripPersistence.encodeFinished(LiveTripState(finished = fin)),
            null,
        )
        assertEquals(LiveTripState(open, fin), r.state)
        assertFalse(r.migrated)
    }

    @Test
    fun restoreMigratesLegacySingleString() {
        val legacy = "L4|7,1,2,17000.0,,,,17001.0,,,,1,2,0.5,0.0,0.1,0.0,DRIVE|5,6,12.4,,0.6,,10,20,0.5,0.5,0.0,0.0|E"
        val r = LiveTripPersistence.restore(null, null, legacy)
        assertTrue(r.migrated)
        assertEquals(7L, r.state.open!!.anchorMs)
        assertEquals(5L, r.state.finished.single().startMs)
    }

    @Test
    fun restoreIgnoresLegacyOnceNewKeysExist() {
        val legacy = LiveTripCodec.encode(LiveTripState(finished = fin))
        val r = LiveTripPersistence.restore(LiveTripPersistence.encodeOpen(LiveTripState()), null, legacy)
        assertFalse(r.migrated)
        assertTrue(r.state.finished.isEmpty())
    }

    @Test
    fun restoreOfNothingIsEmpty() {
        val r = LiveTripPersistence.restore(null, null, null)
        assertEquals(LiveTripState(), r.state)
        assertFalse(r.migrated)
    }
}
