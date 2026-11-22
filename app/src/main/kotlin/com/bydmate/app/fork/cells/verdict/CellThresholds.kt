package com.bydmate.app.fork.cells.verdict

enum class Level { NONE, OK, WATCH, SERVICE }

/** Zone of a measurement with the research thresholds in mV; [strongRefMv] is the reference of the «strongly» cell class. */
enum class ZoneRow(val normMv: Int, val serviceMv: Int?, val strongRefMv: Int?) {
    PLATEAU(20, 90, 90),
    TOP_KNEE(20, null, 90),
    TOP(150, 300, 300),
    LOW_10_15(50, null, 150),
    LOW_UNDER_10(150, null, null),
    LOAD(50, 200, 200),
}

private const val TOP_VMAX_MV = 3400
private const val KNEE_SOC = 85
private const val LOW_SOC = 15
private const val VERY_LOW_SOC = 10

/** Unknown SOC decides by Vmax only (R10). */
fun zoneOf(vmaxMv: Int, soc: Int?): ZoneRow = when {
    vmaxMv > TOP_VMAX_MV -> ZoneRow.TOP
    soc != null && soc > KNEE_SOC -> ZoneRow.TOP_KNEE
    soc != null && soc < VERY_LOW_SOC -> ZoneRow.LOW_UNDER_10
    soc != null && soc < LOW_SOC -> ZoneRow.LOW_10_15
    else -> ZoneRow.PLATEAU
}

enum class Origin { BYD, STANDARD, MEASURED, COMMUNITY, INFERENCE }

/** One row of the help table; the UI maps [conditionKey] to a string. [row] is null for rows without a zone. */
data class ThresholdRow(val row: ZoneRow?, val conditionKey: String, val origins: List<Origin>)

val THRESHOLD_ROWS: List<ThresholdRow> = listOf(
    ThresholdRow(ZoneRow.PLATEAU, "plateau", listOf(Origin.BYD, Origin.COMMUNITY, Origin.MEASURED)),
    ThresholdRow(ZoneRow.TOP_KNEE, "top_knee", listOf(Origin.INFERENCE)),
    ThresholdRow(ZoneRow.TOP, "top", listOf(Origin.STANDARD, Origin.COMMUNITY)),
    ThresholdRow(ZoneRow.LOW_10_15, "low_10_15", listOf(Origin.COMMUNITY, Origin.INFERENCE)),
    ThresholdRow(ZoneRow.LOW_UNDER_10, "low_under_10", listOf(Origin.COMMUNITY, Origin.INFERENCE)),
    ThresholdRow(ZoneRow.LOAD, "load", listOf(Origin.COMMUNITY, Origin.INFERENCE)),
    ThresholdRow(null, "bms_flag", listOf(Origin.BYD)),
)
