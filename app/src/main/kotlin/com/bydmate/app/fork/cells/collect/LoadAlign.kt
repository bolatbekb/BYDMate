package com.bydmate.app.fork.cells.collect

import com.bydmate.app.fork.cells.verdict.CellVerdict
import kotlin.math.roundToInt

private const val CELLS_PER_PAGE = 3

/**
 * A load array set on one current. The 0x43D pages come over 6.3 s while the current swings, so a raw
 * array under load mixes voltages taken at different currents: full sweeps from the owner's Leopard 5
 * (2026-11-23) read 28–112 mV of spread where the cells were 3–9 mV apart. The cells of one page are
 * measured together, so each cell keeps its offset from its page-mates' median, laid on the array's
 * median. A page with fewer cells than [perPage] (the last one) is set against the page before it.
 * The cost: a whole page or module sagging together is not seen under load (the rest rows still see it).
 */
internal fun alignedUnderLoad(cellsMv: IntArray, perPage: Int = CELLS_PER_PAGE): IntArray {
    if (cellsMv.size < perPage) return cellsMv.copyOf()
    val center = CellVerdict.median(cellsMv)
    val pages = (cellsMv.size + perPage - 1) / perPage
    val pageRef = DoubleArray(pages)
    for (p in 0 until pages) {
        val from = p * perPage
        val to = minOf(cellsMv.size, from + perPage)
        pageRef[p] = if (to - from == perPage || p == 0) {
            CellVerdict.median(cellsMv.copyOfRange(from, to))
        } else {
            pageRef[p - 1]
        }
    }
    return IntArray(cellsMv.size) { i -> (cellsMv[i] - pageRef[i / perPage] + center).roundToInt() }
}
