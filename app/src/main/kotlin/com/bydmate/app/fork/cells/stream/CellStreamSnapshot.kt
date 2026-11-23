package com.bydmate.app.fork.cells.stream

/** Immutable copy of the assembler state; arrays are owned by the snapshot. */
data class CellStreamSnapshot(
    val nowMs: Long,
    val listening: Boolean,
    val registration: Int,
    val listeningSinceMs: Long,
    val lastFrameMs: Long,
    val frames: Long,
    val rejected: Long,
    val declaredCells: Int,
    val declaredProbes: Int,
    val cellMv: IntArray,
    val cellPageMs: LongArray,
    val probeC: IntArray,
    val probePageMs: LongArray,
    val hottest: Int,
    val hottestC: Int,
    val coldest: Int,
    val coldestC: Int,
)

sealed interface CellReading {
    data class Complete(
        val cellsMv: IntArray,
        val probesC: IntArray,
        val oldestPageMs: Long,
        val newestPageMs: Long,
    ) : CellReading

    data class Incomplete(val missingCellPages: Int, val missingProbePages: Int) : CellReading
    data class Mismatch(val cells: Int, val probes: Int) : CellReading
    data object NoFrames : CellReading
}

private const val CELLS_PER_PAGE = 3
private const val PROBES_PER_PAGE = 6
private const val MIN_CELL_MV = 2000
private const val MAX_CELL_MV = 4000
private const val MIN_PROBE_C = -40
private const val MAX_PROBE_C = 125

private fun pagesFor(count: Int, per: Int): Int = (count + per - 1) / per

/** Number of pages holding at least one value outside [range]. */
private fun badPages(values: IntArray, perPage: Int, range: IntRange): Int =
    (0 until pagesFor(values.size, perPage)).count { p ->
        (p * perPage until minOf(values.size, (p + 1) * perPage)).any { values[it] !in range }
    }

private fun stalePages(s: CellStreamSnapshot, at: LongArray, pages: Int, windowStartMs: Long): Int =
    (0 until pages).count { val t = at[it]; !(t > 0 && t >= windowStartMs && s.nowMs - t <= FRESH_MS) }

fun readingOf(s: CellStreamSnapshot, expectedCells: Int, expectedProbes: Int, windowStartMs: Long = 0L): CellReading {
    if (s.lastFrameMs == 0L) return CellReading.NoFrames
    val cellPages = pagesFor(expectedCells, CELLS_PER_PAGE)
    val probePages = pagesFor(expectedProbes, PROBES_PER_PAGE)
    if (s.declaredCells < 0 || s.declaredProbes < 0) return CellReading.Incomplete(cellPages, probePages)
    if (s.declaredCells != expectedCells || s.declaredProbes != expectedProbes) {
        return CellReading.Mismatch(s.declaredCells, s.declaredProbes)
    }
    if (cellPages > MAX_CELL_PAGES || probePages > MAX_PROBE_PAGES) return CellReading.Incomplete(cellPages, probePages)

    val staleCells = stalePages(s, s.cellPageMs, cellPages, windowStartMs)
    val staleProbes = stalePages(s, s.probePageMs, probePages, windowStartMs)
    if (staleCells > 0 || staleProbes > 0) return CellReading.Incomplete(staleCells, staleProbes)

    val cells = s.cellMv.copyOf(expectedCells)
    val probes = s.probeC.copyOf(expectedProbes)
    val badCells = badPages(cells, CELLS_PER_PAGE, MIN_CELL_MV..MAX_CELL_MV)
    val badProbes = badPages(probes, PROBES_PER_PAGE, MIN_PROBE_C..MAX_PROBE_C)
    if (badCells > 0 || badProbes > 0) return CellReading.Incomplete(badCells, badProbes)

    val times = (0 until cellPages).map { s.cellPageMs[it] } + (0 until probePages).map { s.probePageMs[it] }
    return CellReading.Complete(cells, probes, times.min(), times.max())
}
