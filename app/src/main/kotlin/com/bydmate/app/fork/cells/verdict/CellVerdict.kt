package com.bydmate.app.fork.cells.verdict

import com.bydmate.app.fork.cells.collect.MeasureKind
import java.time.ZoneId
import kotlin.math.abs

private const val LOAD_SERVICE_MV = 200

object CellVerdict {
    fun median(mv: IntArray): Double {
        val s = mv.sortedArray()
        val n = s.size
        return if (n % 2 == 1) s[n / 2].toDouble() else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** |d| > norm/2 is noticeable, |d| > strongRef/2 strong; both references double when cold. */
    fun classify(deviationsMv: DoubleArray, row: ZoneRow, cold: Boolean): List<CellClass> {
        val k = if (cold) 2 else 1
        val noticeable = row.normMv * k / 2.0
        val strong = row.strongRefMv?.let { it * k / 2.0 }
        return deviationsMv.map { d ->
            val low = d < 0
            when {
                strong != null && abs(d) > strong -> if (low) CellClass.STRONGLY_LOW else CellClass.STRONGLY_HIGH
                abs(d) > noticeable -> if (low) CellClass.NOTICEABLY_LOW else CellClass.NOTICEABLY_HIGH
                else -> CellClass.NORMAL
            }
        }
    }

    fun evaluate(m: MeasurementInput, h: History, zone: ZoneId = ZoneId.systemDefault()): Evaluation {
        if (m.kind == MeasureKind.LOAD) return evaluateLoad(m, h.loads)
        val shape = Shape(m.cellsMv)
        val b = EvalContext(m, zoneOf(shape.vmaxMv, m.soc), isCold(m), shape)
        noVerdictReason(m)?.let { return b.evaluation(Level.NONE, false, listOf(it)) }
        val lag = Laggards.find((if (m.atRest) listOf(m) else emptyList()) + h.rest)
        val base = Baseline.of(m, b.row, h.restSpreads)
        val d = Draft()
        val twice = spreadRules(b, d, serviceTwice(b, h.restSpreads, zone))
        zoneRules(b, d, lag)
        capRules(b, d, lag, base?.let { shape.spreadMv - it })
        val previous = h.restSpreads.filter { it.row == b.row && it.id != m.id }.maxByOrNull { it.ts }?.spreadMv
        return b.evaluation(d.level, prelimReasons(m).isNotEmpty(), d.reasons, lag, twice, base, previous)
    }

    /** Under load: always preliminary; service needs the service number now and before on the same lowest cell. */
    fun evaluateLoad(m: MeasurementInput, previousLoads: List<MeasurementInput>): Evaluation {
        val shape = Shape(m.cellsMv)
        val b = EvalContext(m, ZoneRow.LOAD, isCold(m), shape)
        val svc = b.svc ?: LOAD_SERVICE_MV
        val repeats = shape.spreadMv >= svc && previousLoads.any {
            it.id != m.id && Shape(it.cellsMv).let { s -> s.spreadMv >= svc && s.lowestCell == shape.lowestCell }
        }
        val reasons = mutableListOf<Reason>()
        var level = Level.OK
        if (shape.spreadMv > b.norm) {
            level = Level.WATCH
            reasons.add(Reason.SPREAD_ABOVE_NORM)
        }
        if (shape.spreadMv >= svc) reasons.add(if (repeats) Reason.SERVICE_TWICE else Reason.SERVICE_ONCE)
        if (repeats) level = Level.SERVICE
        if (b.cold) {
            level = minOf(level, Level.WATCH)
            reasons.add(Reason.COLD)
        }
        return b.evaluation(level, true, reasons, twice = repeats)
    }
}
