package com.bydmate.app.fork.cells.demo

import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.cells.data.SourceReply
import com.bydmate.app.fork.cells.stream.CellReading
import com.bydmate.app.fork.cells.stream.CellTx
import com.bydmate.app.fork.cells.stream.readingOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCellSourceTest {
    private fun car(soc: Int, amps: Double, temp: Int): DiParsData = mockk {
        every { this@mockk.soc } returns soc
        every { hvCurrent } returns amps
        every { avgBatTemp } returns temp
    }

    @Test fun `demo source always gives a complete 184 by 24 reading`() = runBlocking {
        val live = MutableStateFlow<DiParsData?>(null)
        val src = DemoCellSource(live) { 50_000L }
        val empty = (src.snapshot() as SourceReply.Ok).value
        assertTrue(readingOf(empty, 184, 24) is CellReading.Complete)
        live.value = car(80, 30.0, 12)
        val reading = readingOf((src.snapshot() as SourceReply.Ok).value, 184, 24) as CellReading.Complete
        assertEquals(50_000L, reading.newestPageMs)
        assertTrue(reading.cellsMv.average() > 3280)
    }

    @Test fun `listen answers ok`() = runBlocking {
        val r = DemoCellSource(MutableStateFlow(null)).listen(true) as SourceReply.Ok
        assertEquals(CellTx.REG_OK, r.value.registration)
    }
}
