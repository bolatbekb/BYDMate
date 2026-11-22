package com.bydmate.app.fork.cells.verdict

import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.collect.Relax
import com.bydmate.app.fork.cells.data.CellBlob
import com.bydmate.app.fork.cells.stream.CellTx
import com.bydmate.app.fork.cells.stream.feedFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CellVerdictTest {
    private fun ev(x: MeasurementInput, h: History = hist()) = CellVerdict.evaluate(x, h, ZoneOffset.UTC)

    @Test fun `zones`() {
        assertEquals(ZoneRow.TOP, zoneOf(3401, 50))
        assertEquals(ZoneRow.TOP_KNEE, zoneOf(3390, 86))
        assertEquals(ZoneRow.LOW_10_15, zoneOf(3300, 14))
        assertEquals(ZoneRow.LOW_UNDER_10, zoneOf(3300, 9))
        assertEquals(ZoneRow.PLATEAU, zoneOf(3300, 50))
        assertEquals(ZoneRow.PLATEAU, zoneOf(3300, null))
    }

    @Test fun `plateau norm is 20 mV`() {
        assertEquals(Level.OK, ev(m(20)).level)
        val w = ev(m(21))
        assertEquals(Level.WATCH, w.level)
        assertEquals(listOf(Reason.SPREAD_ABOVE_NORM), w.reasons)
    }

    @Test fun `service never by one measurement`() {
        val e = ev(m(90))
        assertEquals(Level.WATCH, e.level)
        assertTrue(Reason.SERVICE_ONCE in e.reasons)
    }

    @Test fun `service when twice on different days`() {
        val e = ev(m(90), hist(sp(2, NOON - DAY, 95)))
        assertEquals(Level.SERVICE, e.level)
        assertTrue(Reason.SERVICE_TWICE in e.reasons)
        assertTrue(e.service.twiceOnDifferentDays)
        assertEquals(95, e.previousSpreadMv)
    }

    @Test fun `same day or preliminary prior does not count`() {
        assertEquals(Level.WATCH, ev(m(90), hist(sp(2, NOON - 3_600_000, 95))).level)
        assertEquals(Level.WATCH, ev(m(90), hist(sp(2, NOON - DAY, 95, prelim = true))).level)
    }

    @Test fun `no verdict below 15 minutes of rest or while charging`() {
        val s = ev(m(30, rest = 14))
        assertEquals(Level.NONE, s.level)
        assertEquals(listOf(Reason.REST_SHORT), s.reasons)
        val c = ev(m(30, charging = true))
        assertEquals(Level.NONE, c.level)
        assertEquals(listOf(Reason.CHARGING), c.reasons)
    }

    @Test fun `manual under current is not a rest measurement`() {
        val e = ev(m(30, kind = MeasureKind.MANUAL, period = null, currentA = 20.0))
        assertEquals(Level.NONE, e.level)
        assertEquals(listOf(Reason.REST_UNKNOWN), e.reasons)
        assertEquals(Level.WATCH, ev(m(30, kind = MeasureKind.MANUAL)).level)
    }

    @Test fun `rest 15 to 60 minutes is preliminary and never red`() {
        val e = ev(m(120, rest = 15), hist(sp(2, NOON - DAY, 130)))
        assertEquals(Level.WATCH, e.level)
        assertTrue(e.preliminary)
        assertTrue(Reason.REST_PRELIM in e.reasons)
    }

    @Test fun `unknown rest is preliminary`() {
        val e = ev(m(10, rest = null))
        assertTrue(e.preliminary)
        assertEquals(listOf(Reason.REST_UNKNOWN), e.reasons)
        assertEquals(Level.OK, e.level)
    }

    @Test fun `relaxing measurement is preliminary`() {
        val r = ev(m(10, relax = Relax.RELAXING))
        assertTrue(r.preliminary)
        assertTrue(Reason.RELAXING in r.reasons)
        for (x in listOf(Relax.PENDING, Relax.UNKNOWN)) {
            val e = ev(m(10, relax = x))
            assertTrue(e.preliminary)
            assertTrue(Reason.RELAX_UNKNOWN in e.reasons)
        }
        assertFalse(ev(m(10, relax = Relax.SETTLED)).preliminary)
    }

    @Test fun `cold doubles the thresholds and caps at watch`() {
        val ok = ev(m(35, probe = 9))
        assertEquals(Level.OK, ok.level)
        assertEquals(40, ok.limits.normMv)
        val w = ev(m(200, probe = 9), hist(sp(2, NOON - DAY, 210)))
        assertEquals(Level.WATCH, w.level)
        assertTrue(Reason.COLD in w.reasons)
    }

    @Test fun `top after charge`() {
        fun top(spread: Int, vararg p: SpreadPoint) = ev(m(spread, soc = 95, vmax = 3450, daysSinceFull = 3), hist(*p))
        assertEquals(Level.OK, top(150).level)
        assertEquals(Level.WATCH, top(151).level)
        assertEquals(Level.SERVICE, top(300, sp(2, NOON - DAY, 310, ZoneRow.TOP)).level)
    }

    @Test fun `top service needs a full charge in the last 7 days`() {
        for (d in listOf(8, null)) {
            val e = ev(m(300, soc = 95, vmax = 3450, daysSinceFull = d), hist(sp(2, NOON - DAY, 310, ZoneRow.TOP)))
            assertEquals(Level.WATCH, e.level)
            assertTrue(Reason.TOP_NO_RECENT_FULL in e.reasons)
        }
    }

    @Test fun `knee never goes to service`() {
        val e = ev(m(95, soc = 90, vmax = 3390), hist(sp(2, NOON - DAY, 99, ZoneRow.TOP_KNEE)))
        assertEquals(Level.WATCH, e.level)
        assertTrue(Reason.KNEE_REMEASURE in e.reasons)
    }

    @Test fun `low zone norms`() {
        assertEquals(Level.OK, ev(m(50, soc = 12)).level)
        assertEquals(Level.WATCH, ev(m(51, soc = 12)).level)
        assertEquals(Level.OK, ev(m(150, soc = 8)).level)
        assertEquals(Level.WATCH, ev(m(151, soc = 8)).level)
    }

    @Test fun `low zone service only with the same laggard`() {
        // current + 5 prior rest periods; cell 92 is the 12 mV minimum in 3 of the 6
        fun prior(lowestOf: (Long) -> Int) = (2L..6L).map { m(12, soc = 50, id = it, ts = NOON - it * DAY, lowest = lowestOf(it)) }
        val without = ev(m(60, soc = 12), hist(rest = emptyList()))
        assertEquals(Level.WATCH, without.level)
        val e = ev(m(60, soc = 12), hist(rest = prior { if (it <= 3) 91 else 10 + it.toInt() }))
        assertEquals(Level.SERVICE, e.level)
        assertTrue(Reason.LOW_WITH_LAGGARD in e.reasons)
        val other = ev(m(60, soc = 12), hist(rest = prior { 30 + it.toInt() }))
        assertEquals(Level.WATCH, other.level)
    }

    @Test fun `laggard raises ok to watch`() {
        val prior = (2L..3L).map { m(12, id = it, ts = NOON - it * DAY) }
        val e = ev(m(12), hist(rest = prior))
        assertEquals(Level.WATCH, e.level)
        assertEquals(listOf(Laggard(92, 3, 3)), e.laggards)
        assertTrue(e.service.laggardRepeats)
    }

    @Test fun `healthy pack with a 2 mV minimum shows no laggard`() {
        val prior = (2L..6L).map { m(2, id = it, ts = NOON - it * DAY) }
        val e = ev(m(2), hist(rest = prior))
        assertEquals(Level.OK, e.level)
        assertTrue(e.laggards.isEmpty())
    }

    @Test fun `no base growth reason when cold`() {
        val pts = (0 until 5).map { sp(10L + it, NOON - (40 + it) * DAY, 5 + it) }
        val e = ev(m(38, probe = 5), hist(*pts.toTypedArray()))
        assertEquals(31, e.growthMv)
        assertFalse(Reason.BASE_GROWTH in e.reasons)
    }

    @Test fun `toInput is null for empty cells and an unknown measure`() {
        val e = com.bydmate.app.fork.cells.data.CellSnapshotEntity(
            ts = NOON, elapsedMs = 0, profile = "p", measure = "AFTER_PARKING", restPeriod = 1, relax = "NOT_NEEDED", relaxDropMv = null,
            soc = 50, currentA = 0.0, packV = 580, charging = false, restMin = 70, daysSinceFull = 1, vminMv = 3270, vmaxMv = 3275,
            spreadMv = 5, probeMinC = 20, probeMaxC = 22, flagConsistency = null, flagTempDiff = null, flagUnbalanced = null,
            cells = CellBlob.encodeCells(cells(5)), probes = CellBlob.encodeProbes(IntArray(24) { 20 }),
        )
        assertEquals(MeasureKind.AFTER_PARKING, e.toInput()!!.kind)
        assertEquals(null, e.copy(cells = ByteArray(0)).toInput())
        assertEquals(null, e.copy(measure = "BOGUS").toInput())
        assertEquals(null, e.copy(measure = null).toInput())
        assertEquals(Relax.UNKNOWN, e.copy(relax = "BOGUS").toInput()!!.relax)
    }

    @Test fun `threshold rows pin their origins`() {
        fun o(key: String) = THRESHOLD_ROWS.single { it.conditionKey == key }.origins
        assertEquals(listOf(Origin.MEASURED, Origin.STANDARD, Origin.COMMUNITY), o("plateau"))
        assertEquals(listOf(Origin.MEASURED), o("top_knee"))
        assertEquals(listOf(Origin.MEASURED, Origin.COMMUNITY, Origin.STANDARD), o("top"))
        assertEquals(listOf(Origin.MEASURED, Origin.COMMUNITY, Origin.INFERENCE), o("low_10_15"))
        assertEquals(listOf(Origin.MEASURED, Origin.COMMUNITY, Origin.INFERENCE), o("low_under_10"))
        assertEquals(listOf(Origin.COMMUNITY, Origin.INFERENCE), o("load"))
        assertEquals(listOf(Origin.BYD), o("bms_flag"))
    }

    @Test fun `base growth raises to watch`() {
        val pts = (0 until 5).map { sp(10L + it, NOON - (40 + it) * DAY, 5 + it) }
        val e = ev(m(38), hist(*pts.toTypedArray()))
        assertEquals(7, e.baseMv)
        assertEquals(31, e.growthMv)
        assertTrue(Reason.BASE_GROWTH in e.reasons)
    }

    @Test fun `median of an even count is the mean of the middle two`() {
        val snap = feedFixture().snapshot(true, CellTx.REG_OK, 0L)
        val mv = snap.cellMv.copyOf(snap.declaredCells)
        assertEquals(184, mv.size)
        assertEquals(3275.0, CellVerdict.median(mv), 0.0)
        assertEquals(2.5, CellVerdict.median(intArrayOf(1, 2, 3, 4)), 0.0)
    }

    @Test fun `cell classes by zone`() {
        fun c(d: Double, row: ZoneRow, cold: Boolean = false) = CellVerdict.classify(doubleArrayOf(d), row, cold).single()
        assertEquals(CellClass.NOTICEABLY_LOW, c(-11.0, ZoneRow.PLATEAU))
        assertEquals(CellClass.STRONGLY_LOW, c(-46.0, ZoneRow.PLATEAU))
        assertEquals(CellClass.NOTICEABLY_HIGH, c(11.0, ZoneRow.PLATEAU))
        assertEquals(CellClass.NORMAL, c(-10.0, ZoneRow.PLATEAU))
        assertEquals(CellClass.NOTICEABLY_LOW, c(-76.0, ZoneRow.TOP))
        assertEquals(CellClass.STRONGLY_LOW, c(-151.0, ZoneRow.TOP))
        assertEquals(CellClass.NOTICEABLY_LOW, c(-21.0, ZoneRow.PLATEAU, cold = true))
        assertEquals(CellClass.NOTICEABLY_LOW, c(-200.0, ZoneRow.LOW_UNDER_10))
    }

    @Test fun `service conditions`() {
        val e = ev(m(90), hist(sp(2, NOON - DAY, 95), rest = (2L..3L).map { m(12, id = it, ts = NOON - it * DAY) }))
        assertTrue(e.service.twiceOnDifferentDays)
        assertTrue(e.service.laggardRepeats)
        assertTrue(e.service.cellBelowHalfService)
        assertFalse(ev(m(10)).service.cellBelowHalfService)
    }

    @Test fun `input from an entity round trips and survives bad strings`() {
        val summary = com.bydmate.app.fork.cells.data.SnapshotSummary(
            1, NOON, "BOGUS", 1, "WHATEVER", 50, 0.0, false, 70, 3, 3300, 3290, 10, 5, 8,
        )
        val p = summary.toSpreadPoint()
        assertTrue(p.cold)
        assertTrue(p.preliminary)
        assertEquals(ZoneRow.PLATEAU, p.row)
        assertEquals(184 * 2, CellBlob.encodeCells(cells(5)).size)
    }
}
