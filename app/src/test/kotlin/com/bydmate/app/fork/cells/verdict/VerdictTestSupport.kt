package com.bydmate.app.fork.cells.verdict

import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.collect.Relax
import com.bydmate.app.fork.cells.stream.NO_TEMP

internal const val DAY = 86_400_000L
internal const val NOON = 1_800_000_000_000L - 1_800_000_000_000L % DAY + 12 * 3_600_000L

internal fun cells(spread: Int, lowest: Int = 91, base: Int = 3275): IntArray =
    IntArray(184) { base }.also { it[lowest] = base - spread }

@Suppress("LongParameterList") // why: test builder with defaults
internal fun m(
    spread: Int = 5,
    soc: Int? = 50,
    rest: Int? = 70,
    id: Long = 1,
    ts: Long = NOON,
    period: Long? = id,
    kind: MeasureKind = MeasureKind.AFTER_PARKING,
    lowest: Int = 91,
    vmax: Int = 3275,
    probe: Int = 20,
    relax: Relax = Relax.NOT_NEEDED,
    charging: Boolean = false,
    daysSinceFull: Int? = 2,
    currentA: Double? = 0.5,
) = MeasurementInput(
    id, ts, kind, cells(spread, lowest, vmax), IntArray(24) { probe }.also { it[3] = NO_TEMP }, soc, currentA, 580, rest, charging,
    relax, daysSinceFull, period,
)

@Suppress("LongParameterList") // why: test builder with defaults
internal fun sp(
    id: Long,
    ts: Long,
    spread: Int,
    row: ZoneRow = ZoneRow.PLATEAU,
    cold: Boolean = false,
    prelim: Boolean = false,
) = SpreadPoint(id, ts, id, row, spread, cold, prelim)

internal fun hist(vararg s: SpreadPoint, rest: List<MeasurementInput> = emptyList(), loads: List<MeasurementInput> = emptyList()) =
    History(rest, s.toList(), loads)
