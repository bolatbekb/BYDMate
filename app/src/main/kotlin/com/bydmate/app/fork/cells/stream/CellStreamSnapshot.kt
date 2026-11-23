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

    /** Frames come, but the pack has not said yet how many cells and probes it has (0x43A). */
    data object WaitingForCount : CellReading

    /**
     * The pack's own counts cannot be read as they are: out of the arrays' reach, or fresh pages come past the count (the
     * count would cut the pack short). Never read in part.
     */
    data class Unsupported(val cells: Int, val probes: Int) : CellReading
    data object NoFrames : CellReading
}

private const val CELLS_PER_PAGE = 3
private const val PROBES_PER_PAGE = 6
private const val MIN_CELL_MV = 2000
private const val MAX_CELL_MV = 4000
private const val MIN_PROBE_C = -40
private const val MAX_PROBE_C = 125

/** The most cells and probes the assembler's arrays hold (the helper's wire carries the same sizes). */
const val MAX_CELLS = MAX_CELL_PAGES * CELLS_PER_PAGE
const val MAX_PROBES = MAX_PROBE_PAGES * PROBES_PER_PAGE

private fun pagesFor(count: Int, per: Int): Int = (count + per - 1) / per

/** Number of pages holding at least one value outside [range]. */
private fun badPages(values: IntArray, perPage: Int, range: IntRange): Int =
    (0 until pagesFor(values.size, perPage)).count { p ->
        (p * perPage until minOf(values.size, (p + 1) * perPage)).any { values[it] !in range }
    }

private fun fresh(s: CellStreamSnapshot, t: Long, windowStartMs: Long): Boolean = t > 0 && t >= windowStartMs && s.nowMs - t <= FRESH_MS

private fun stalePages(s: CellStreamSnapshot, at: LongArray, pages: Int, windowStartMs: Long): Int =
    (0 until pages).count { !fresh(s, at[it], windowStartMs) }

/** A fresh page past [pages] that [holds] a value: the stream has more than the count says. */
private fun pagesPast(s: CellStreamSnapshot, at: LongArray, pages: Int, holds: (Int) -> Boolean): Boolean =
    (pages until at.size).any { p -> fresh(s, at[p], 0L) && holds(p) }

private fun cellPageHolds(s: CellStreamSnapshot, page: Int): Boolean =
    (page * CELLS_PER_PAGE until minOf(s.cellMv.size, (page + 1) * CELLS_PER_PAGE)).any { s.cellMv[it] != NO_VALUE }

private fun probePageHolds(s: CellStreamSnapshot, page: Int): Boolean =
    (page * PROBES_PER_PAGE until minOf(s.probeC.size, (page + 1) * PROBES_PER_PAGE)).any { s.probeC[it] != NO_TEMP }

/** The declared pack fits the arrays (1…[MAX_CELLS] cells, up to [MAX_PROBES] probes) and no fresh page comes past it. */
private fun readable(s: CellStreamSnapshot, cells: Int, probes: Int): Boolean {
    val cellPages = pagesFor(cells, CELLS_PER_PAGE)
    val probePages = pagesFor(probes, PROBES_PER_PAGE)
    val fits = cells in 1..MAX_CELLS && probes <= MAX_PROBES && cells <= s.cellMv.size && probes <= s.probeC.size &&
        cellPages <= s.cellPageMs.size && probePages <= s.probePageMs.size
    return fits && !pagesPast(s, s.cellPageMs, cellPages) { cellPageHolds(s, it) } &&
        !pagesPast(s, s.probePageMs, probePages) { probePageHolds(s, it) }
}

/**
 * The pack as the car declares it in 0x43A: that many cells (pages of three) and probes (pages of six), every page
 * fresher than [FRESH_MS] and not before [windowStartMs], every value plausible.
 */
fun readingOf(s: CellStreamSnapshot, windowStartMs: Long = 0L): CellReading {
    if (s.lastFrameMs == 0L) return CellReading.NoFrames
    val cells = s.declaredCells
    val probes = s.declaredProbes
    if (cells < 0 || probes < 0) return CellReading.WaitingForCount
    if (!readable(s, cells, probes)) return CellReading.Unsupported(cells, probes)
    val cellPages = pagesFor(cells, CELLS_PER_PAGE)
    val probePages = pagesFor(probes, PROBES_PER_PAGE)

    val staleCells = stalePages(s, s.cellPageMs, cellPages, windowStartMs)
    val staleProbes = stalePages(s, s.probePageMs, probePages, windowStartMs)
    if (staleCells > 0 || staleProbes > 0) return CellReading.Incomplete(staleCells, staleProbes)

    val cellsMv = s.cellMv.copyOf(cells)
    val probesC = s.probeC.copyOf(probes)
    val badCells = badPages(cellsMv, CELLS_PER_PAGE, MIN_CELL_MV..MAX_CELL_MV)
    val badProbes = badPages(probesC, PROBES_PER_PAGE, MIN_PROBE_C..MAX_PROBE_C)
    if (badCells > 0 || badProbes > 0) return CellReading.Incomplete(badCells, badProbes)

    val times = (0 until cellPages).map { s.cellPageMs[it] } + (0 until probePages).map { s.probePageMs[it] }
    return CellReading.Complete(cellsMv, probesC, times.min(), times.max())
}
