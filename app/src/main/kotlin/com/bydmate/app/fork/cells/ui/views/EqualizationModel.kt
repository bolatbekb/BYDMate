package com.bydmate.app.fork.cells.ui.views

import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.data.SnapshotSummary
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.ZoneRow
import com.bydmate.app.fork.cells.verdict.zoneOf
import kotlin.math.abs

// The model of «Выравнивание»: after a charge, the spread by the hour after its end (R22); otherwise the
// rest spread of the last two weeks, one point per parking. fork-local.

enum class EqMode { AFTER_CHARGE, PARKINGS }

/**
 * What the chart is drawn from, read on IO in bounded windows. AFTER_CHARGE: [points] are the snapshots of the 18 h before the
 * measurement, [anchorTs] the charge end (the latest snapshot with charging = true among them) or null: then the curve starts
 * at the wake (R22). [previous] are the snapshots of the earlier period that reached SOC 100, from [previousAnchorTs] on (its
 * charge end or its wake). PARKINGS: [points] are the rest measurements of the 14 days before, one per rest period.
 */
data class EqualizationData(
    val mode: EqMode,
    val points: List<SnapshotSummary>,
    val anchorTs: Long?,
    val previous: List<SnapshotSummary>,
    val previousAnchorTs: Long?,
)

/**
 * One dot: [x] in hours after the charge end or the wake (AFTER_CHARGE) or in days, 14 = the measurement (PARKINGS).
 * [grey]: within the first hour after a charge end, no verdict there. [level] colours a parking's dot (never red without a
 * verdict), [preliminary] draws it hollow; [current] is the selected measurement itself.
 */
data class EqPoint(
    val x: Double,
    val mv: Int,
    val grey: Boolean,
    val level: Level? = null,
    val preliminary: Boolean = false,
    val current: Boolean = false,
)

/** [cells] are (1-based cell, deviation from the median, mV): the five highest after a charge, else the five lowest. */
data class EqualizationModel(
    val mode: EqMode,
    val awakeAxis: Boolean,
    val points: List<EqPoint>,
    val previous: List<EqPoint>,
    val normMv: Int,
    val serviceMv: Int?,
    val xMax: Double,
    val cells: List<Pair<Int, Double>>,
)

internal const val HOUR_MS = 3_600_000L
internal const val EQ_WINDOW_H = 18
internal const val EQ_WINDOW_MS = EQ_WINDOW_H * HOUR_MS
private const val BUCKET_H = 0.25
private const val BUCKETS = (EQ_WINDOW_H / BUCKET_H).toInt()
private const val FIRST_HOUR_BUCKETS = (1 / BUCKET_H).toInt()
private const val SESSION_GAP_MS = 15 * 60_000L
private const val LOAD_A = 10.0

fun eqModeOf(e: Evaluation): EqMode =
    if (e.row == ZoneRow.TOP || e.input.kind == MeasureKind.AFTER_CHARGE) EqMode.AFTER_CHARGE else EqMode.PARKINGS

/** [levels]: the evaluated level of a measurement by id, for the parkings' dots. */
fun equalizationModel(e: Evaluation, d: EqualizationData, levels: Map<Long, Level> = emptyMap()): EqualizationModel = when (d.mode) {
    EqMode.AFTER_CHARGE -> afterChargeModel(e, d)
    EqMode.PARKINGS -> parkingsModel(e, d, levels)
}

/**
 * The chart's norm and service lines: the measurement's own limits (cold doubles them) when it belongs to the drawn zone —
 * the top after a charge, the plateau for the parkings — else that zone's research numbers.
 */
fun eqLines(e: Evaluation, mode: EqMode): Pair<Int, Int?> {
    val zone = if (mode == EqMode.AFTER_CHARGE) ZoneRow.TOP else ZoneRow.PLATEAU
    return if (e.row == zone) e.limits.normMv to e.limits.serviceMv else zone.normMv to zone.serviceMv
}

private fun afterChargeModel(e: Evaluation, d: EqualizationData): EqualizationModel {
    val me = e.input
    val sorted = d.points.sortedBy { it.ts }
    val awake = d.anchorTs == null
    val origin = d.anchorTs ?: wakeStart(sorted, me.ts)
    val x = hours(me.ts - origin).coerceIn(0.0, EQ_WINDOW_H.toDouble())
    val mine = EqPoint(x, e.spreadMv, grey = !awake && x < 1.0, current = true)
    val before = curve(sorted.filter { it.id != me.id && it.ts <= me.ts }, origin, !awake, skip = bucketOf(x))
    val previous = d.previousAnchorTs?.let { curve(d.previous.sortedBy { it.ts }, it, greyFirstHour = false, skip = null) }.orEmpty()
    val (norm, svc) = eqLines(e, EqMode.AFTER_CHARGE)
    return EqualizationModel(
        EqMode.AFTER_CHARGE, awake, (before + mine).sortedBy { it.x }, previous, norm, svc, EQ_WINDOW_H.toDouble(), extremeCells(e, highest = true),
    )
}

/**
 * The curve from [origin]: snapshots not charging and not under load, within 18 h, in 15-minute buckets (median spread,
 * mean time each), so 10⁴ snapshots draw as at most 72 dots. The bucket [skip] is the measurement's own: its exact dot wins.
 */
private fun curve(rows: List<SnapshotSummary>, origin: Long, greyFirstHour: Boolean, skip: Int?): List<EqPoint> =
    rows.asSequence()
        .filter { !it.charging && abs(it.currentA ?: 0.0) <= LOAD_A && it.ts >= origin && it.ts - origin <= EQ_WINDOW_MS }
        .groupBy { bucketOf(hours(it.ts - origin)) }
        .filterKeys { it != skip }
        .toSortedMap()
        .map { (i, list) -> EqPoint(list.map { hours(it.ts - origin) }.average(), medianOf(list.map { it.spreadMv }), greyFirstHour && i < FIRST_HOUR_BUCKETS) }

/**
 * R22 without a charge end: the first top-zone snapshot of the session that holds the measurement at [ts] (snapshots at most
 * 15 minutes apart, walking back from it); the measurement itself when there is none. [sorted] is oldest first.
 */
internal fun wakeStart(sorted: List<SnapshotSummary>, ts: Long): Long {
    val upTo = sorted.filter { it.ts <= ts }
    if (upTo.isEmpty() || ts - upTo.last().ts > SESSION_GAP_MS) return ts
    var i = upTo.lastIndex
    while (i > 0 && upTo[i].ts - upTo[i - 1].ts <= SESSION_GAP_MS) i--
    return upTo.subList(i, upTo.size).firstOrNull { isTop(it) }?.ts ?: ts
}

/** Top zone: the highest cell above 3,40 V or SOC above 85 %. */
private fun isTop(s: SnapshotSummary): Boolean = zoneOf(s.vmaxMv, s.soc).let { it == ZoneRow.TOP || it == ZoneRow.TOP_KNEE }

private fun hours(ms: Long): Double = ms / HOUR_MS.toDouble()

private fun bucketOf(h: Double): Int = (h / BUCKET_H).toInt().coerceIn(0, BUCKETS - 1)

private fun medianOf(v: List<Int>): Int {
    val s = v.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2] + 1) / 2
}
