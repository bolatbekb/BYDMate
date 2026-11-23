package com.bydmate.app.fork.cells.collect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellScheduleTest {
    private val sec = 1000L
    private val min = 60 * sec
    private val base = 1_000_000_000L

    private fun sample(tSec: Long, current: Double?, soc: Int? = 56, charging: Boolean = false) =
        LiveSample(base + tSec * sec, 5_000_000L + tSec * sec, true, soc, current, 330, charging)

    private fun el(tSec: Long) = 5_000_000L + tSec * sec

    private fun anchor(agoMin: Long, soc: Int? = 56) =
        SessionAnchor(base - agoMin * min, 5_000_000L - agoMin * min, soc)

    private fun schedule(intervalSec: Long = 30) = CellSchedule({ 54.0 }, { intervalSec * sec })

    private fun CellSchedule.begin(a: SessionAnchor?) = startSession(sample(0, 0.0), a)

    private fun stored(id: Long, tSec: Long, vmax: Int, measure: MeasureKind?, soc: Int? = 56) =
        StoredSnapshot(id, el(tSec), vmax, soc, measure)

    @Test fun `wake asks for the after-parking snapshot with rest from the anchor`() {
        val s = schedule()
        s.begin(anchor(540))
        val step = s.onSample(sample(0, 0.0))
        assertTrue(step.due.afterParking)
        assertEquals(540, s.restMinutes(el(0)))
        assertEquals(base - 540 * min, s.restPeriod)
    }

    @Test fun `unknown rest is null until 60 known minutes`() {
        val s = schedule()
        s.begin(null)
        s.onSample(sample(0, 0.0))
        assertNull(s.restMinutes(el(0)))
        for (t in 10..3600 step 10) s.onSample(sample(t.toLong(), 0.0))
        assertEquals(60, s.restMinutes(el(3600)))
        assertEquals(base, s.restPeriod)
    }

    @Test fun `scheduled every interval after any stored snapshot`() {
        val s = schedule()
        s.begin(anchor(5))
        assertTrue(s.onSample(sample(0, 0.0)).due.scheduled)
        s.onStored(sample(0, 0.0), stored(1, 0, 3300, MeasureKind.AFTER_PARKING))
        assertFalse(s.onSample(sample(10, 0.0)).due.scheduled)
        assertFalse(s.onSample(sample(20, 0.0)).due.scheduled)
        assertTrue(s.onSample(sample(30, 0.0)).due.scheduled)
    }

    @Test fun `rest runs at 5 A, holds at 7 A, resets above 10 A`() {
        val s = schedule()
        s.begin(anchor(0))
        for (t in 0..600 step 10) s.onSample(sample(t.toLong(), 5.0))
        assertEquals(10, s.restMinutes(el(600)))
        for (t in 610..1200 step 10) s.onSample(sample(t.toLong(), 7.0))
        assertEquals(10, s.restMinutes(el(1200)))
        s.onSample(sample(1210, 11.0))
        assertEquals(0, s.restMinutes(el(1210)))
    }

    @Test fun `a gap over 60 s counts as 60 s`() {
        val s = schedule()
        s.begin(anchor(0))
        s.onSample(sample(0, 0.0))
        s.onSample(sample(600, 0.0))
        assertEquals(1, s.restMinutes(el(600)))
    }

    @Test fun `null current changes nothing`() {
        val s = schedule()
        s.begin(anchor(0))
        s.onSample(sample(0, 0.0))
        s.onSample(sample(30, 0.0))
        s.onSample(sample(60, 0.0))
        s.onSample(sample(70, null))
        assertEquals(1, s.restMinutes(el(70)))
        s.onSample(sample(95, 11.0))
        assertEquals(0, s.restMinutes(el(95)))
    }

    @Test fun `charging resets rest and blocks rest measurements`() {
        val s = schedule()
        s.begin(anchor(70))
        s.onSample(sample(0, 0.0))
        assertTrue(s.onSample(sample(10, 0.0)).due.rest60)
        val step = s.onSample(sample(20, 2.0, charging = true))
        assertFalse(step.due.rest60)
        assertFalse(step.due.afterParking)
        assertEquals(0, s.restMinutes(el(20)))
        assertNull(s.restPeriod)
        val back = s.onSample(sample(30, 1.0))
        assertEquals(base + 30 * sec, s.restPeriod)
        assertFalse(back.due.rest60)
    }

    @Test fun `rest measurement at 60 minutes once per rest period`() {
        val s = schedule()
        s.begin(anchor(0))
        s.onSample(sample(0, 0.0))
        s.onStored(sample(0, 0.0), stored(1, 0, 3300, MeasureKind.AFTER_PARKING))
        var firstDue = -1L
        for (t in 10..3600 step 10) {
            if (s.onSample(sample(t.toLong(), 0.0)).due.rest60 && firstDue < 0) firstDue = t.toLong()
        }
        assertEquals(3600L, firstDue)
        s.onStored(sample(3600, 0.0), stored(2, 3600, 3300, MeasureKind.REST_IN_SESSION))
        assertFalse(s.onSample(sample(3610, 0.0)).due.rest60)
        s.onSample(sample(3620, 20.0))
        for (t in 3630..7230 step 10) s.onSample(sample(t.toLong(), 0.0))
        assertTrue(s.onSample(sample(7240, 0.0)).due.rest60)
    }

    @Test fun `a short after-parking measurement gets one more at 60 minutes`() {
        val s = schedule()
        s.begin(anchor(20))
        assertTrue(s.onSample(sample(0, 0.0)).due.afterParking)
        val period = s.restPeriod
        s.onStored(sample(0, 0.0), stored(1, 0, 3300, MeasureKind.AFTER_PARKING))
        assertFalse(s.onSample(sample(10, 0.0)).due.afterParking)
        for (t in 20..2390 step 10) assertFalse(s.onSample(sample(t.toLong(), 0.0)).due.rest60)
        assertTrue(s.onSample(sample(2400, 0.0)).due.rest60)
        assertEquals(period, s.restPeriod)
    }

    @Test fun `a known rest under 15 minutes is no parking`() {
        val short = schedule()
        short.startSession(sample(0, 0.0), anchor(14))
        assertFalse(short.onSample(sample(0, 0.0)).due.afterParking)
        val enough = schedule()
        enough.startSession(sample(0, 0.0), anchor(15))
        assertTrue(enough.onSample(sample(0, 0.0)).due.afterParking)
        val unknown = schedule()
        unknown.startSession(sample(0, 0.0), null)
        assertTrue(unknown.onSample(sample(0, 0.0)).due.afterParking)
    }

    @Test fun `a session joined without a wake asks no after-parking measurement`() {
        val s = schedule()
        s.startSession(sample(0, 0.0), anchor(120), wake = false)
        assertFalse(s.onSample(sample(0, 0.0)).due.afterParking)
        assertEquals(120, s.restMinutes(el(0)))
    }

    @Test fun `after parking is dropped when the car pulls away first`() {
        val s = schedule()
        s.begin(anchor(120))
        assertTrue(s.onSample(sample(0, 0.0)).due.afterParking)
        assertFalse(s.onSample(sample(1, 30.0)).due.afterParking)
        assertFalse(s.onSample(sample(2, 0.0)).due.afterParking)
    }

    @Test fun `load needs 54 A for 7 s with samples at most 3 s apart`() {
        val s = schedule()
        s.begin(null)
        for (t in 0..6) assertFalse(s.onSample(sample(t.toLong(), 60.0)).due.load)
        val d = s.onSample(sample(7, 60.0)).due
        assertTrue(d.load)
        assertEquals(el(0), d.windowStartMs)

        val r = schedule()
        r.begin(null)
        r.onSample(sample(0, 60.0))
        r.onSample(sample(1, 60.0))
        r.onSample(sample(2, 60.0))
        r.onSample(sample(3, 53.0))
        for (t in 4..10) assertFalse(r.onSample(sample(t.toLong(), 60.0)).due.load)
        val d2 = r.onSample(sample(11, 60.0)).due
        assertTrue(d2.load)
        assertEquals(el(4), d2.windowStartMs)

        val g = schedule()
        g.begin(null)
        g.onSample(sample(0, 60.0))
        g.onSample(sample(5, 60.0))
        assertFalse(g.onSample(sample(8, 60.0)).due.load)
    }

    @Test fun `strong regeneration is not a load episode`() {
        // The live current is negative while charging or regenerating: load is discharge only.
        val s = schedule()
        s.begin(null)
        for (t in 0..20) assertFalse(s.onSample(sample(t.toLong(), -60.0)).due.load)
    }

    @Test fun `one load measurement per episode`() {
        val s = schedule()
        s.begin(null)
        for (t in 0..7) s.onSample(sample(t.toLong(), 60.0))
        s.onStored(sample(7, 60.0), stored(1, 7, 3300, MeasureKind.LOAD))
        assertFalse(s.onSample(sample(8, 60.0)).due.load)
        assertFalse(s.onSample(sample(9, 60.0)).due.load)
        s.onSample(sample(10, 40.0))
        for (t in 11..17) assertFalse(s.onSample(sample(t.toLong(), 60.0)).due.load)
        assertTrue(s.onSample(sample(18, 60.0)).due.load)
    }

    @Test fun `relax follow-up five minutes after a top-zone after-parking measurement`() {
        for ((second, outcome, drop) in listOf(
            Triple(3416, Relax.RELAXING, 4), Triple(3417, Relax.SETTLED, 3),
        )) {
            val s = schedule(600)
            s.begin(anchor(120))
            assertTrue(s.onSample(sample(0, 0.0)).due.afterParking)
            assertEquals(Relax.PENDING, s.relaxFor(MeasureKind.AFTER_PARKING, 3420, 56))
            assertTrue(s.onStored(sample(0, 0.0), stored(7, 0, 3420, MeasureKind.AFTER_PARKING)).isEmpty())
            for (t in 10..290 step 10) assertFalse(s.onSample(sample(t.toLong(), 0.0)).due.relaxCheck)
            assertFalse(s.onSample(sample(299, 0.0)).due.relaxCheck)
            assertTrue(s.onSample(sample(300, 0.0)).due.relaxCheck)
            val upd = s.onStored(sample(300, 0.0), stored(8, 300, second, MeasureKind.REST_IN_SESSION))
            assertEquals(listOf(RelaxUpdate(7, outcome, drop)), upd)
            assertFalse(s.onSample(sample(310, 0.0)).due.relaxCheck)
        }
    }

    @Test fun `relax becomes UNKNOWN when the rest breaks or the window passes`() {
        fun armed(): CellSchedule {
            val s = schedule(600)
            s.begin(anchor(120))
            s.onSample(sample(0, 0.0))
            s.onStored(sample(0, 0.0), stored(7, 0, 3420, MeasureKind.AFTER_PARKING))
            return s
        }
        val unknown = listOf(RelaxUpdate(7, Relax.UNKNOWN, null))
        val a = armed()
        a.onSample(sample(100, 0.0))
        assertEquals(unknown, a.onSample(sample(200, 20.0)).relax)

        val b = armed()
        for (t in 10..480 step 10) assertEquals(emptyList<RelaxUpdate>(), b.onSample(sample(t.toLong(), 0.0)).relax)
        assertEquals(unknown, b.onSample(sample(481, 0.0)).relax)
        assertFalse(b.onSample(sample(482, 0.0)).due.relaxCheck)

        assertEquals(unknown, armed().endSession())
    }

    @Test fun `no relax check on the plateau when SOC did not rise`() {
        val s = schedule()
        s.begin(anchor(120, soc = 56))
        s.onSample(sample(0, 0.0))
        assertEquals(Relax.NOT_NEEDED, s.relaxFor(MeasureKind.AFTER_PARKING, 3300, 56))
        assertEquals(Relax.NOT_NEEDED, s.relaxFor(MeasureKind.REST_IN_SESSION, 3420, 56))
    }

    @Test fun `relax check when SOC rose 2 pp since the last session`() {
        val s = schedule()
        s.begin(anchor(120, soc = 54))
        s.onSample(sample(0, 0.0))
        assertEquals(Relax.PENDING, s.relaxFor(MeasureKind.AFTER_PARKING, 3300, 56))
        val t = schedule()
        t.begin(anchor(120, soc = 55))
        assertEquals(Relax.NOT_NEEDED, t.relaxFor(MeasureKind.AFTER_PARKING, 3300, 56))
    }

    @Test fun `keepalive every 60 s even with a 5-minute interval`() {
        val s = schedule(300)
        s.begin(anchor(120))
        s.onSample(sample(0, 0.0))
        s.onStored(sample(0, 0.0), stored(1, 0, 3300, MeasureKind.AFTER_PARKING))
        assertFalse(s.onSample(sample(59, 0.0)).due.keepalive)
        val d = s.onSample(sample(60, 0.0)).due
        assertTrue(d.keepalive)
        assertFalse(d.wantsSnapshot)
        s.onHelperContact(el(60))
        assertFalse(s.onSample(sample(119, 0.0)).due.keepalive)
        assertTrue(s.onSample(sample(120, 0.0)).due.keepalive)
    }

    @Test fun `measureFor picks after charge at the top`() {
        fun due(ap: Boolean = false, r: Boolean = false, l: Boolean = false, sc: Boolean = false) =
            Due(sc, ap, r, l, false, false, 0)
        assertEquals(MeasureKind.AFTER_CHARGE, measureFor(due(ap = true), 3401, 56))
        assertEquals(MeasureKind.AFTER_PARKING, measureFor(due(ap = true), 3400, 94))
        assertEquals(MeasureKind.AFTER_CHARGE, measureFor(due(r = true), 3300, 95))
        assertEquals(MeasureKind.REST_IN_SESSION, measureFor(due(r = true), 3300, null))
        assertEquals(MeasureKind.LOAD, measureFor(due(l = true), 3300, 56))
        assertNull(measureFor(due(sc = true), 3300, 56))
    }

    @Test fun `relaxOutcome threshold is 3 mV`() {
        assertEquals(Relax.RELAXING, relaxOutcome(3420, 3416))
        assertEquals(Relax.SETTLED, relaxOutcome(3420, 3417))
        assertNotNull(Relax.valueOf("PENDING"))
    }
}
