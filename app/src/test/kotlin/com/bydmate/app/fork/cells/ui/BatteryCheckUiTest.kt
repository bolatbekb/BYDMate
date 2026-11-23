package com.bydmate.app.fork.cells.ui

import com.bydmate.app.R
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.CheckView
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.stream.CellReading
import com.bydmate.app.fork.cells.stream.NO_TEMP
import com.bydmate.app.fork.cells.stream.feedFixture
import com.bydmate.app.fork.cells.stream.readingOf
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.ui.theme.AccentOrange
import com.bydmate.app.ui.theme.SocRed
import com.bydmate.app.fork.cells.verdict.CellVerdict
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Laggard
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.MeasurementInput
import com.bydmate.app.fork.cells.verdict.Origin
import com.bydmate.app.fork.cells.verdict.Reason
import com.bydmate.app.fork.cells.verdict.THRESHOLD_ROWS
import com.bydmate.app.fork.cells.verdict.ZoneRow
import com.bydmate.app.fork.cells.verdict.hist
import com.bydmate.app.fork.cells.verdict.m
import com.bydmate.app.fork.cells.verdict.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class BatteryCheckUiTest {
    private val car = CellsConfig(CarProfile.LEOPARD5_318, collect = true, intervalSec = 30, view = CheckView.WHAT_TO_DO)
    private val ok = StreamStatus(StreamState.OK)
    private val nov22 = LocalDateTime.of(2026, 11, 22, 19, 46).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun eval(input: MeasurementInput = m(spread = 4, ts = nov22)): Evaluation = CellVerdict.evaluate(input, hist(), ZoneOffset.UTC)

    @Test fun `entry row is hidden without a car`() {
        assertEquals(EntryKind.HIDDEN, entryRow(car.copy(profile = CarProfile.NONE), ok, eval()).kind)
        assertEquals(EntryKind.HIDDEN, entryRow(car.copy(profile = CarProfile.NONE, collect = false), ok, null).kind)
    }

    @Test fun `entry row asks to turn collection on`() {
        assertEquals(EntryKind.COLLECT_OFF, entryRow(car.copy(collect = false), StreamStatus(StreamState.IDLE), null).kind)
        assertEquals(EntryKind.NO_MEASUREMENTS, entryRow(car, ok, null).kind)
    }

    @Test fun `entry row says the data does not match`() {
        assertEquals(EntryKind.UNSUPPORTED, entryRow(car, StreamStatus(StreamState.UNSUPPORTED, 120, 16), eval()).kind)
        assertEquals(EntryKind.UNSUPPORTED, entryRow(car.copy(collect = false), StreamStatus(StreamState.UNSUPPORTED, 120, 16), null).kind)
    }

    @Test fun `entry row tells a stopped stream from one that never came`() {
        val never = entryRow(car, StreamStatus(StreamState.NO_STREAM), null)
        assertEquals(EntryKind.NO_STREAM, never.kind)
        assertFalse(never.stopped)
        val stopped = entryRow(car, StreamStatus(StreamState.NO_STREAM), eval(), framesSeen = true)
        assertEquals(EntryKind.NO_STREAM, stopped.kind)
        assertTrue(stopped.stopped)
    }

    @Test fun `entry row shows the latest result`() {
        val r = entryRow(car, ok, eval())
        assertEquals(EntryRow(EntryKind.RESULT, Level.OK, preliminary = false, spreadMv = 4, dateMs = nov22), r)
        assertEquals("22.11", dayMonth(r.dateMs!!, ZoneOffset.UTC))
        assertEquals("22.11, 19:46", dayMonthTime(nov22, ZoneOffset.UTC))
        // an old result stays on the row with collection off
        assertEquals(EntryKind.RESULT, entryRow(car.copy(collect = false), StreamStatus(StreamState.IDLE), eval()).kind)
    }

    @Test fun `deviation groups worst first without empty groups`() {
        val base = m(spread = 0)
        val cells = base.cellsMv.copyOf().also {
            it[10] = 3275 - 60
            it[20] = 3275 - 70
            it[30] = 3275 - 15
            it[40] = 3275 + 15
            it[50] = 3275 + 12
        }
        val groups = deviationGroups(eval(base.copy(cellsMv = cells)))
        assertEquals(
            listOf(
                DeviationGroup(CellClass.STRONGLY_LOW, listOf(21, 11)),
                DeviationGroup(CellClass.NOTICEABLY_LOW, listOf(31)),
                DeviationGroup(CellClass.NOTICEABLY_HIGH, listOf(41, 51)),
            ),
            groups,
        )
        assertTrue(deviationGroups(eval()).isEmpty())
    }

    @Test fun `probe tiles mark one hottest and one coldest`() {
        val probes = (readingOf(feedFixture().snapshot(true, 0, 0L)) as CellReading.Complete).probesC
        val tiles = probeTiles(probes)
        assertEquals(24, tiles.size)
        assertEquals((1..24).toList(), tiles.map { it.index })
        assertEquals(1, tiles.single { it.hottest }.index)
        assertEquals(4, tiles.single { it.coldest }.index)
        assertEquals(20, tiles[0].tempC)
        val gap = probeTiles(intArrayOf(20, NO_TEMP, 19))
        assertNull(gap[1].tempC)
        assertEquals(3, gap.single { it.coldest }.index)
        // all equal: nothing stands out
        assertTrue(probeTiles(IntArray(24) { 20 }).none { it.hottest || it.coldest })
    }

    @Test fun `conditions line for after parking`() {
        val input = m(rest = 540, currentA = 3.1).copy(probesC = IntArray(24) { if (it == 3 || it == 7) 19 else 20 })
        assertEquals(
            listOf(ConditionPart.Kind(MeasureKind.AFTER_PARKING), ConditionPart.Rest(540), ConditionPart.Current(3.1), ConditionPart.Probes(19, 20)),
            conditionsLine(eval(input)),
        )
        val manual = m(rest = null, currentA = null, kind = MeasureKind.MANUAL, probe = NO_TEMP)
        assertEquals(listOf(ConditionPart.Kind(MeasureKind.MANUAL), ConditionPart.RestUnknown), conditionsLine(eval(manual)))
        val load = m(kind = MeasureKind.LOAD, rest = null, period = null, currentA = -80.0)
        assertEquals(
            listOf(ConditionPart.Kind(MeasureKind.LOAD), ConditionPart.Current(-80.0), ConditionPart.Probes(20, 20)),
            conditionsLine(eval(load)),
        )
    }

    @Test fun `help lists the spec rows`() {
        assertEquals(7, THRESHOLD_ROWS.size)
        val texts = THRESHOLD_ROWS.map { helpRow(it.conditionKey) }
        assertTrue(texts.none { it == null })
        assertEquals(7, texts.distinct().size)
        assertNull(helpRow("dc_charge"))
        assertTrue(THRESHOLD_ROWS.all { it.origins.isNotEmpty() })
        assertEquals(Origin.entries.size, Origin.entries.map { originRes(it) }.distinct().size)
    }

    @Test fun `every reason has its text`() {
        val e = eval().copy(
            reasons = Reason.entries,
            laggards = listOf(Laggard(92, 3, 6)),
            baseMv = 8,
            growthMv = 35,
            previousSpreadMv = 95,
        )
        val texts = reasonTexts(e)
        assertEquals(Reason.entries.size, texts.size)
        assertEquals(Reason.entries.size, texts.map { it.res }.distinct().size)
        assertEquals(listOf<Any>(92, 3, 6), texts[Reason.entries.indexOf(Reason.LAGGARD)].args)
    }

    @Test fun `history points take the evaluated level and are never red without it`() {
        // without a verdict only the norm band counts: one measurement never says «в сервис»
        assertEquals(Level.OK, pointLevel(sp(1, 0, 18)))
        assertEquals(Level.WATCH, pointLevel(sp(1, 0, 40)))
        assertEquals(Level.WATCH, pointLevel(sp(1, 0, 95)))
        assertEquals(Level.WATCH, pointLevel(sp(1, 0, 95, prelim = true)))
        assertEquals(Level.WATCH, pointLevel(sp(1, 0, 320, row = ZoneRow.TOP)))
        assertEquals(Level.OK, pointLevel(sp(1, 0, 140, row = ZoneRow.TOP)))
        // cold doubles the norm
        assertEquals(Level.OK, pointLevel(sp(1, 0, 38, cold = true)))
        assertEquals(Level.WATCH, pointLevel(sp(1, 0, 200, cold = true)))
        // the verdict wins: the second measurement over 90 mV on another day is red, a capped one is not
        assertEquals(Level.SERVICE, pointLevel(sp(1, 0, 95), known = Level.SERVICE))
        assertEquals(Level.WATCH, pointLevel(sp(1, 0, 320, row = ZoneRow.TOP), known = Level.WATCH))
        assertEquals(Level.NONE, pointLevel(sp(1, 0, 95, prelim = true), known = Level.NONE))
    }

    @Test fun `strong cells are red only with a verdict and not in the cold`() {
        assertEquals(SocRed, cellColor(CellClass.STRONGLY_LOW, Level.OK))
        assertEquals(SocRed, cellColor(CellClass.STRONGLY_HIGH, Level.WATCH, cold = false))
        assertEquals(AccentOrange, cellColor(CellClass.STRONGLY_LOW, Level.OK, cold = true))
        assertEquals(AccentOrange, cellColor(CellClass.STRONGLY_HIGH, Level.WATCH, cold = true))
        assertEquals(AccentOrange, cellColor(CellClass.STRONGLY_LOW, Level.NONE))
    }

    @Test fun `the selected measurement stays in the picker`() {
        val listed = PickerItem(5, MeasureKind.AFTER_PARKING, nov22, 4, Level.OK, false)
        assertEquals(listOf(listed), pickerWithSelection(listOf(listed), null))
        val e = eval(m(spread = 6, id = 9, ts = nov22 - 40 * 86_400_000L))
        val withOld = pickerWithSelection(listOf(listed), e)
        assertEquals(listOf(5L, 9L), withOld.map { it.id })
        assertEquals(Level.OK, withOld[1].level)
        assertEquals(6, withOld[1].spreadMv)
        // already listed: unchanged
        assertEquals(listOf(listed), pickerWithSelection(listOf(listed), eval(m(id = 5, ts = nov22))))
    }

    @Test fun `an error of the manual snapshot has its own text`() {
        assertEquals(TextRes(R.string.fork_cells_manual_error), manualText(ManualUi.Error))
        assertNull(manualText(ManualUi.Idle))
    }

    @Test fun `signed deviation uses a real minus`() {
        assertEquals("−24", signedMv(-24.0))
        assertEquals("+15", signedMv(15.4))
        assertEquals("0", signedMv(0.2))
    }
}
