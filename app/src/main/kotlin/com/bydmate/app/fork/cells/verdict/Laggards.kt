package com.bydmate.app.fork.cells.verdict

private const val WINDOW = 6
private const val MIN_MEASUREMENTS = 3
private const val MIN_TIMES = 3
private const val MIN_GAP_MV = 10 // BMS accuracy floor, GB/T 38661

/** Honest measurements only. Cells that were the lowest or noticeably low in at least 3 of the newest 6 rest periods. */
object Laggards {
    /** One measurement per rest period; the list is newest first and may include the current measurement. */
    fun find(restNewestFirst: List<MeasurementInput>): List<Laggard> {
        val picked = restNewestFirst.filter { it.honest }.distinctBy { it.restPeriod }.take(WINDOW)
        if (picked.size < MIN_MEASUREMENTS) return emptyList()
        val counts = IntArray(picked.first().cellsMv.size)
        for (m in picked) {
            val min = m.cellsMv.min()
            val gapMv = MIN_GAP_MV * (if (isCold(m)) 2 else 1)
            val med = CellVerdict.median(m.cellsMv)
            val cls = CellVerdict.classify(DoubleArray(m.cellsMv.size) { m.cellsMv[it] - med }, zoneOf(m.cellsMv.max(), m.soc), isCold(m))
            for (i in m.cellsMv.indices) {
                val low = cls[i] == CellClass.NOTICEABLY_LOW || cls[i] == CellClass.STRONGLY_LOW
                val deepMin = m.cellsMv[i] == min && med - min >= gapMv
                if (i < counts.size && (low || deepMin)) counts[i]++
            }
        }
        return counts.indices.filter { counts[it] >= MIN_TIMES }.map { Laggard(it + 1, counts[it], picked.size) }.sortedByDescending { it.times }
    }
}
