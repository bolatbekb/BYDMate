package com.bydmate.app.fork.cells.demo

import android.os.SystemClock
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.fork.cells.data.CellSource
import com.bydmate.app.fork.cells.data.SourceReply
import com.bydmate.app.fork.cells.stream.CellListenReply
import com.bydmate.app.fork.cells.stream.CellStreamSnapshot
import com.bydmate.app.fork.cells.stream.CellTx
import com.bydmate.app.fork.cells.stream.MAX_CELL_PAGES
import com.bydmate.app.fork.cells.stream.MAX_PROBE_PAGES
import com.bydmate.app.fork.cells.stream.NO_TEMP
import com.bydmate.app.fork.cells.stream.NO_VALUE
import kotlinx.coroutines.flow.StateFlow

private const val DEFAULT_SOC = 56
private const val DEFAULT_TEMP_C = 20
private const val CELLS_PER_PAGE = 3
private const val PROBES_PER_PAGE = 6
private const val LISTENING_FOR_MS = 60_000L
private const val FRAMES = 1_000L

/** The cell stream of the demo car: the model follows the demo car's SOC, current and battery temperature. Never touches the helper. */
class DemoCellSource(
    private val live: StateFlow<DiParsData?>,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : CellSource {
    override suspend fun listen(on: Boolean): SourceReply<CellListenReply> =
        SourceReply.Ok(CellListenReply(CellTx.REG_OK, "demo"))

    override suspend fun snapshot(): SourceReply<CellStreamSnapshot> {
        val d = live.value
        val now = clock()
        val cells = DemoCellModel.cellsMv((d?.soc ?: DEFAULT_SOC).toDouble(), d?.hvCurrent ?: 0.0, WEAKNESS)
        val probes = DemoCellModel.probesC(d?.avgBatTemp ?: DEFAULT_TEMP_C)
        val cellPages = (cells.size + CELLS_PER_PAGE - 1) / CELLS_PER_PAGE
        val probePages = (probes.size + PROBES_PER_PAGE - 1) / PROBES_PER_PAGE
        val hot = probes.indices.maxBy { probes[it] }
        val cold = probes.indices.minBy { probes[it] }
        return SourceReply.Ok(
            CellStreamSnapshot(
                nowMs = now, listening = true, registration = CellTx.REG_OK, listeningSinceMs = now - LISTENING_FOR_MS,
                lastFrameMs = now, frames = FRAMES, rejected = 0, declaredCells = cells.size, declaredProbes = probes.size,
                cellMv = IntArray(MAX_CELL_PAGES * CELLS_PER_PAGE) { if (it < cells.size) cells[it] else NO_VALUE },
                cellPageMs = LongArray(MAX_CELL_PAGES) { if (it < cellPages) now else 0L },
                probeC = IntArray(MAX_PROBE_PAGES * PROBES_PER_PAGE) { if (it < probes.size) probes[it] else NO_TEMP },
                probePageMs = LongArray(MAX_PROBE_PAGES) { if (it < probePages) now else 0L },
                hottest = hot + 1, hottestC = probes[hot], coldest = cold + 1, coldestC = probes[cold],
            ),
        )
    }

    private companion object {
        const val WEAKNESS = 1.0
    }
}
