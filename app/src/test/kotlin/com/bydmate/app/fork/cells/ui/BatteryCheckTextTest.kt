package com.bydmate.app.fork.cells.ui

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.bydmate.app.R
import com.bydmate.app.fork.cells.collect.ManualProblem
import com.bydmate.app.fork.cells.collect.ManualResult
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.CheckView
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.verdict.CellVerdict
import com.bydmate.app.fork.cells.verdict.hist
import com.bydmate.app.fork.cells.verdict.m
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import java.util.Locale

/** The window's texts resolved against the real Russian resources, nested arguments included. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "ru")
class BatteryCheckTextTest {
    private lateinit var r: Resources

    @Before fun setUp() {
        r = ApplicationProvider.getApplicationContext<Context>().resources
    }

    @Test fun `a rough manual snapshot names every problem`() {
        val t = manualText(ManualUi.Done(ManualResult.Stored(1, false, listOf(ManualProblem.CURRENT, ManualProblem.COLD))))!!
        assertEquals("Замер снят, но условия неточные: идёт ток, холодно.", resolve(t, r))
        assertEquals("Не удалось снять замер: машина не передаёт данные", resolve(manualText(ManualUi.Done(ManualResult.Failed(StreamState.NO_STREAM)))!!, r))
    }

    @Test fun `the footer reads as one sentence`() {
        val input = m(rest = 540, currentA = 3.1).copy(probesC = IntArray(24) { if (it == 3) 19 else 20 })
        val e = CellVerdict.evaluate(input, hist(), ZoneOffset.UTC)
        val text = resolve(footerText(e, Locale("ru")), r)
        assertTrue(text, text.startsWith("Замер после стоянки, покой 9 ч, ток 3,1 А, датчики 19–20 °C. Зона: плато 15–85 %, условия порога — покой ≥ 1 ч."))
    }

    @Test fun `durations, percent signs and the collection line`() {
        assertEquals("покой 1 ч 20 мин", resolve(TextRes(R.string.fork_cells_part_rest, durationText(80)), r))
        assertEquals("45 мин", resolve(durationText(45), r))
        assertEquals("Зарядите до 100 % от переменного тока и повторите замер.", resolve(TextRes(R.string.fork_cells_reason_top_no_full), r))
        val car = CellsConfig(CarProfile.LEOPARD5_318, collect = true, intervalSec = 30, view = CheckView.WHAT_TO_DO)
        assertEquals("Сбор данных: вкл · снимок раз в 30 с", resolve(collectText(car), r))
        assertEquals("Данные ячеек перестали приходить — проверьте, включена ли машина.", resolve(streamText(StreamStatus(StreamState.NO_STREAM), framesSeen = true)!!, r))
    }
}
