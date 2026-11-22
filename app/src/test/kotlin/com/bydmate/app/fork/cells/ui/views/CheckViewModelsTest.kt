package com.bydmate.app.fork.cells.ui.views

import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.collect.Relax
import com.bydmate.app.fork.cells.data.SnapshotSummary
import com.bydmate.app.fork.cells.verdict.CellVerdict
import com.bydmate.app.fork.cells.verdict.DAY
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.MeasurementInput
import com.bydmate.app.fork.cells.verdict.NOON
import com.bydmate.app.fork.cells.verdict.Reason
import com.bydmate.app.fork.cells.verdict.ZoneRow
import com.bydmate.app.fork.cells.verdict.hist
import com.bydmate.app.fork.cells.verdict.m
import com.bydmate.app.fork.cells.verdict.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class CheckViewModelsTest {
    private val hour = 3_600_000L

    private fun eval(input: MeasurementInput, vararg h: com.bydmate.app.fork.cells.verdict.SpreadPoint): Evaluation =
        CellVerdict.evaluate(input, hist(*h), ZoneOffset.UTC)

    private fun highlighted(model: WhatToDoModel): Set<Recheck> = model.recheck.filter { it.second }.map { it.first }.toSet()

    @Suppress("LongParameterList") // why: test builder with defaults
    private fun summary(
        id: Long,
        ts: Long,
        spread: Int,
        charging: Boolean = false,
        vmax: Int = 3450,
        soc: Int? = 100,
        measure: String? = null,
        period: Long? = null,
    ) = SnapshotSummary(
        id, ts, measure, period, "NOT_NEEDED", soc, 0.4, charging, null, 0, vmax, vmax - spread, spread, 20, 21,
    )

    private fun afterCharge(ts: Long, spread: Int = 120): Evaluation =
        eval(m(spread = spread, soc = 100, vmax = 3450, kind = MeasureKind.AFTER_CHARGE, ts = ts, id = 9_999))

    @Test fun `what to do for a healthy rest measurement`() {
        val w = whatToDoModel(eval(m(spread = 4)))
        assertEquals(Advice.NOTHING_TO_DO, w.advice)
        assertEquals(setOf(Recheck.MONTHLY), highlighted(w))
        assertEquals(Recheck.entries.toList(), w.recheck.map { it.first })
        assertEquals(listOf(ZoneRow.PLATEAU), w.norms.filter { it.current }.map { it.row })
        assertEquals(listOf(ZoneRow.LOW_UNDER_10, ZoneRow.LOW_10_15, ZoneRow.PLATEAU, ZoneRow.TOP_KNEE, ZoneRow.TOP), w.norms.map { it.row })
        assertEquals(listOf(150, 50, 20, 20, 150), w.norms.map { it.normMv })
        assertEquals(listOf(null, null, 90, null, 300), w.norms.map { it.serviceMv })
        assertEquals(4, w.yoursMv)
        assertFalse(w.service.twiceOnDifferentDays || w.service.laggardRepeats || w.service.cellBelowHalfService)
    }

    @Test fun `what to do while relaxing`() {
        val e = eval(m(spread = 8, soc = 100, vmax = 3450, kind = MeasureKind.AFTER_CHARGE, relax = Relax.RELAXING))
        assertTrue(Reason.RELAXING in e.reasons)
        val w = whatToDoModel(e)
        assertEquals(Advice.WAIT_RELAX, w.advice)
        assertTrue(Recheck.AFTER_CHARGE_HOUR in highlighted(w))
        assertFalse(Recheck.MONTHLY in highlighted(w))
    }

    @Test fun `what to do highlights the full AC charge`() {
        val before = sp(2, NOON - 2 * DAY, 320, row = ZoneRow.TOP)
        val e = eval(m(spread = 320, soc = 100, vmax = 3450, kind = MeasureKind.AFTER_CHARGE, daysSinceFull = 20), before)
        assertTrue(Reason.TOP_NO_RECENT_FULL in e.reasons)
        val w = whatToDoModel(e)
        assertEquals(Advice.WATCH, w.advice)
        assertTrue(Recheck.AFTER_FULL_AC in highlighted(w))
        assertTrue(w.service.twiceOnDifferentDays)
        assertEquals(listOf(ZoneRow.TOP), w.norms.filter { it.current }.map { it.row })
    }

    @Test fun `what to do without a measurement or a verdict`() {
        val none = whatToDoModel(null)
        assertEquals(Advice.NO_VERDICT, none.advice)
        assertNull(none.yoursMv)
        assertTrue(highlighted(none).isEmpty())
        val short = whatToDoModel(eval(m(spread = 4, rest = 5)))
        assertEquals(Advice.NO_VERDICT, short.advice)
        assertTrue(Recheck.AFTER_PARKING_HOUR in highlighted(short))
        val prelim = whatToDoModel(eval(m(spread = 4, rest = 30)))
        assertEquals(Advice.PRELIMINARY, prelim.advice)
        assertTrue(Recheck.AFTER_PARKING_HOUR in highlighted(prelim))
        assertFalse(Recheck.MONTHLY in highlighted(prelim))
        assertTrue(Recheck.WARM in highlighted(whatToDoModel(eval(m(spread = 4, probe = 5)))))
    }

    @Test fun `norm chart zones follow the spec`() {
        val n = normChartModel(eval(m(spread = 4, soc = 56)))
        assertEquals(listOf(NormZone(0, 10, 150, null), NormZone(10, 15, 50, null), NormZone(15, 85, 20, 90), NormZone(85, 100, 150, 300)), n.zones)
        assertEquals(56 to 4, n.here)
        assertEquals(Level.OK, n.level)
        assertNull(n.previous)
        val empty = normChartModel(null)
        assertEquals(n.zones, empty.zones)
        assertNull(empty.here)
    }

    @Test fun `norm chart scale`() {
        assertEquals(0f, normChartY(0), 0f)
        assertEquals(1f, normChartY(320), 0f)
        assertEquals(1f, normChartY(5_000), 0f)
        assertTrue(normChartY(20) < normChartY(90))
        assertTrue(normChartY(90) < normChartY(150))
    }

    @Test fun `ruler top and value`() {
        val calm = rulerModel(eval(m(spread = 4)))
        assertEquals(117, calm.topMv)
        assertEquals(4, calm.valueMv)
        assertEquals(20, calm.normMv)
        assertEquals(90, calm.serviceMv)
        assertEquals(RulerCheckKind.entries.toList(), calm.checks.map { it.kind })
        assertEquals(230, rulerModel(eval(m(spread = 200))).topMv)
        // a zone without a service number scales to twice its norm
        val knee = rulerModel(eval(m(spread = 4, soc = 90)))
        assertNull(knee.serviceMv)
        assertEquals(52, knee.topMv)
    }

    @Test fun `ruler probe check judges only the cold`() {
        val warm = m(spread = 4).let { it.copy(probesC = IntArray(24) { i -> 19 + i % 9 }) }
        assertEquals(Level.OK, rulerModel(eval(warm)).checks.first { it.kind == RulerCheckKind.PROBES }.level)
        val cold = m(spread = 4, probe = 9)
        assertEquals(Level.WATCH, rulerModel(eval(cold)).checks.first { it.kind == RulerCheckKind.PROBES }.level)
    }

    @Test fun `ruler spread check never says service on one measurement`() {
        val once = rulerModel(eval(m(spread = 120)))
        assertEquals(Level.WATCH, once.checks.first { it.kind == RulerCheckKind.SPREAD }.level)
        val twice = rulerModel(eval(m(spread = 120), sp(2, NOON - 2 * DAY, 120)))
        assertEquals(Level.SERVICE, twice.checks.first { it.kind == RulerCheckKind.SPREAD }.level)
        assertEquals(Level.OK, once.checks.first { it.kind == RulerCheckKind.CONDITIONS }.level)
    }

    @Test fun `equalization after charge greys the first hour`() {
        val t0 = NOON - 2 * hour
        val e = afterCharge(t0 + 90 * 60_000L)
        val d = EqualizationData(
            EqMode.AFTER_CHARGE,
            listOf(summary(1, t0, 300, charging = true), summary(2, t0 + 30 * 60_000L, 200), summary(9_999, e.input.ts, 120)),
            t0,
            emptyList(),
            null,
        )
        val q = equalizationModel(e, d)
        assertEquals(EqMode.AFTER_CHARGE, q.mode)
        assertFalse(q.awakeAxis)
        assertEquals(2, q.points.size)
        assertTrue(q.points[0].grey)
        assertEquals(0.5, q.points[0].x, 1e-6)
        assertFalse(q.points[1].grey)
        assertEquals(1.5, q.points[1].x, 1e-6)
        assertEquals(120, q.points[1].mv)
        assertEquals(18.0, q.xMax, 0.0)
        assertEquals(150, q.normMv)
        assertEquals(300, q.serviceMv)
    }

    @Test fun `equalization buckets 2 000 snapshots to at most 72 points`() {
        val t0 = NOON - 18 * hour
        val e = afterCharge(NOON)
        val step = 18 * hour / 2_000
        val rows = listOf(summary(1, t0, 300, charging = true)) + (1..2_000).map { i -> summary(i + 1L, t0 + i * step, 300 - i / 10) }
        val q = equalizationModel(e, EqualizationData(EqMode.AFTER_CHARGE, rows, t0, rows, t0))
        assertTrue("${q.points.size}", q.points.size <= 72)
        assertTrue(q.points.size >= 70)
        assertTrue(q.points.zipWithNext().all { (a, b) -> a.x < b.x })
        assertTrue(q.points.all { it.x in 0.0..18.0 })
        // the previous curve is bucketed the same way and has no exact measurement point
        assertTrue("${q.previous.size}", q.previous.size in 70..72)
    }

    @Test fun `equalization without a charging snapshot uses the wake axis`() {
        val wake = NOON - 40 * 60_000L
        // a snapshot of the evening before, then the session from the wake on: 30 s apart
        val evening = summary(1, NOON - 14 * hour, 30, vmax = 3290, soc = 60)
        val session = (0..80).map { i -> summary(i + 2L, wake + i * 30_000L, 200 - i) }
        val e = afterCharge(session.last().ts, spread = 120)
        val q = equalizationModel(e, EqualizationData(EqMode.AFTER_CHARGE, listOf(evening) + session, null, emptyList(), null))
        assertTrue(q.awakeAxis)
        assertTrue(q.points.isNotEmpty())
        assertTrue(q.points.none { it.grey })
        assertEquals(0.0, q.points.first().x, 0.2)
        assertTrue(q.points.all { it.x <= 1.0 })
    }

    @Test fun `equalization parkings mode lists the five lowest cells`() {
        val cellsMv = IntArray(184) { 3275 }.also {
            it[10] = 3275 - 40
            it[20] = 3275 - 12
            it[30] = 3275 - 30
            it[40] = 3275 - 25
            it[50] = 3275 - 18
            it[60] = 3275 - 3
            it[70] = 3275 + 9
        }
        val e = eval(m(spread = 0).copy(cellsMv = cellsMv))
        val before = (1..13).map { d -> summary(100L + d, e.input.ts - d * DAY, 10 + d, vmax = 3275, soc = 50, measure = "AFTER_PARKING", period = 100L + d) }
        val rest = listOf(summary(e.input.id, e.input.ts, e.spreadMv, vmax = 3275, soc = 50, measure = "AFTER_PARKING", period = e.input.restPeriod)) + before
        val q = equalizationModel(e, EqualizationData(EqMode.PARKINGS, rest.reversed(), null, emptyList(), null))
        assertEquals(EqMode.PARKINGS, q.mode)
        assertEquals(listOf(11, 31, 41, 51, 21), q.cells.map { it.first })
        assertEquals(-40.0, q.cells.first().second, 1e-6)
        assertEquals(20, q.normMv)
        assertEquals(90, q.serviceMv)
        assertEquals(14.0, q.xMax, 0.0)
        assertEquals(14, q.points.size)
        assertEquals(14.0, q.points.last().x, 1e-6)
        assertTrue(q.points.zipWithNext().all { (a, b) -> a.x < b.x })
        assertEquals(listOf(true), q.points.filter { it.current }.map { it.mv == e.spreadMv })
    }

    @Test fun `equalization after charge lists the five highest cells`() {
        val base = m(spread = 0, soc = 100, vmax = 3450, kind = MeasureKind.AFTER_CHARGE)
        val cellsMv = base.cellsMv.copyOf().also {
            it[158] = 3450 + 70
            it[160] = 3450 + 64
            it[166] = 3450 + 58
            it[169] = 3450 + 52
            it[155] = 3450 + 44
            it[30] = 3450 - 58
        }
        val e = eval(base.copy(cellsMv = cellsMv))
        val q = equalizationModel(e, EqualizationData(EqMode.AFTER_CHARGE, emptyList(), null, emptyList(), null))
        assertEquals(listOf(159, 161, 167, 170, 156), q.cells.map { it.first })
        assertEquals(70.0, q.cells.first().second, 1e-6)
    }

    @Test fun `equalization mode follows the selected measurement`() {
        assertEquals(EqMode.AFTER_CHARGE, eqModeOf(afterCharge(NOON)))
        assertEquals(EqMode.PARKINGS, eqModeOf(eval(m(spread = 4))))
    }
}
