package com.bydmate.app.fork.cells.demo

import kotlin.math.abs
import kotlin.math.roundToInt

/** Model of the demo pack: the real 184 cells and 24 probes of the car at rest, an OCV curve, and one cell that weakens. */
object DemoCellModel {
    /** The last reading of every cell of the real car at rest (SOC 56 %, 20 °C), mV. */
    val REAL_CELLS_MV: IntArray = intArrayOf(
        3273, 3272, 3273, 3273, 3273, 3273, 3273, 3273, 3273, 3273, 3273, 3273, 3274, 3273, 3273, 3274,
        3273, 3273, 3273, 3274, 3274, 3273, 3273, 3274, 3273, 3274, 3273, 3273, 3273, 3273, 3273, 3272,
        3274, 3273, 3273, 3273, 3273, 3273, 3274, 3274, 3273, 3272, 3273, 3273, 3273, 3273, 3272, 3273,
        3273, 3273, 3273, 3274, 3273, 3273, 3274, 3273, 3274, 3273, 3273, 3274, 3274, 3274, 3274, 3274,
        3273, 3275, 3274, 3275, 3275, 3275, 3275, 3275, 3275, 3275, 3275, 3275, 3274, 3275, 3273, 3273,
        3273, 3273, 3273, 3272, 3272, 3273, 3274, 3273, 3273, 3274, 3273, 3272, 3274, 3274, 3274, 3274,
        3274, 3274, 3274, 3274, 3274, 3274, 3274, 3274, 3274, 3274, 3273, 3274, 3275, 3275, 3274, 3275,
        3275, 3275, 3275, 3275, 3275, 3275, 3274, 3274, 3275, 3275, 3274, 3275, 3275, 3274, 3274, 3274,
        3273, 3274, 3274, 3274, 3274, 3274, 3274, 3274, 3274, 3273, 3273, 3274, 3274, 3274, 3273, 3274,
        3274, 3274, 3274, 3274, 3274, 3274, 3274, 3275, 3274, 3274, 3275, 3275, 3275, 3275, 3275, 3276,
        3275, 3276, 3275, 3275, 3275, 3275, 3275, 3276, 3275, 3276, 3275, 3274, 3275, 3274, 3274, 3275,
        3274, 3275, 3273, 3274, 3273, 3274, 3274, 3274
    )

    /** The last reading of every temperature probe of the real car, °C. */
    val REAL_PROBES_C: IntArray = intArrayOf(
        20, 19, 20, 19, 20, 19, 20, 19, 20, 19, 20, 20, 20, 20, 20, 20, 20, 19, 19, 20, 20, 20, 20, 20

    )

    const val WEAK_CELL = 92 // 1-based

    private const val BASE_R_MOHM = 1.1
    private const val WEAK_R_MOHM = 0.8
    private const val WEAK_REST_MV = 40.0
    private const val TOP_VMAX_MV = 3400
    private const val FADE_A = 5.0
    private val OCV = arrayOf(
        0.0 to 2900.0, 5.0 to 3150.0, 10.0 to 3200.0, 15.0 to 3245.0, 30.0 to 3262.0,
        56.0 to 3273.0, 70.0 to 3290.0, 85.0 to 3330.0, 95.0 to 3380.0, 100.0 to 3440.0,
    )
    private val MEDIAN_MV: Int = REAL_CELLS_MV.sorted()[REAL_CELLS_MV.size / 2]

    fun ocvMv(soc: Double): Int {
        val s = soc.coerceIn(0.0, 100.0)
        val i = OCV.indexOfLast { it.first <= s }.coerceAtMost(OCV.size - 2)
        val (x0, y0) = OCV[i]
        val (x1, y1) = OCV[i + 1]
        return (y0 + (y1 - y0) * (s - x0) / (x1 - x0)).roundToInt()
    }

    /** Current: positive discharges the pack (cells sag), negative charges it. */
    fun cellsMv(soc: Double, currentA: Double, weakness: Double): IntArray {
        val ocv = ocvMv(soc)
        val top = ocv > TOP_VMAX_MV
        // the weak cell's offset is the resting one; it fades out as the load grows and the resistance takes over
        val rest = (1.0 - abs(currentA) / FADE_A).coerceIn(0.0, 1.0) * WEAK_REST_MV * weakness * (if (top) 2 else 1)
        return IntArray(REAL_CELLS_MV.size) { i ->
            val weak = i + 1 == WEAK_CELL
            val r = BASE_R_MOHM + if (weak) WEAK_R_MOHM * weakness else 0.0
            (ocv + (REAL_CELLS_MV[i] - MEDIAN_MV) - currentA * r - if (weak) rest else 0.0).roundToInt()
        }
    }

    fun probesC(avgC: Int): IntArray {
        val shift = avgC - REAL_PROBES_C.average().roundToInt()
        return IntArray(REAL_PROBES_C.size) { REAL_PROBES_C[it] + shift }
    }
}
