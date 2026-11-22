package com.bydmate.app.fork.cells.ui

import com.bydmate.app.R
import com.bydmate.app.fork.cells.collect.ManualResult
import com.bydmate.app.fork.cells.data.CellsConfig
import com.bydmate.app.fork.cells.data.StreamState
import com.bydmate.app.fork.cells.data.StreamStatus
import com.bydmate.app.fork.cells.stream.NO_TEMP
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Reason
import java.util.Locale

// Texts with arguments of the battery-check window, as resources: resolved in the composables. fork-local.

private val PLAIN_REASONS = mapOf(
    Reason.REST_SHORT to R.string.fork_cells_reason_rest_short,
    Reason.CHARGING to R.string.fork_cells_reason_charging,
    Reason.REST_UNKNOWN to R.string.fork_cells_reason_rest_unknown,
    Reason.RELAXING to R.string.fork_cells_reason_relaxing,
    Reason.RELAX_UNKNOWN to R.string.fork_cells_reason_relax_unknown,
    Reason.SERVICE_ONCE to R.string.fork_cells_reason_service_once,
    Reason.TOP_NO_RECENT_FULL to R.string.fork_cells_reason_top_no_full,
    Reason.KNEE_REMEASURE to R.string.fork_cells_reason_knee,
)

/** One line per reason of [e], in its order; a reason whose numbers are missing is left out. */
fun reasonTexts(e: Evaluation): List<TextRes> = e.reasons.mapNotNull { r -> PLAIN_REASONS[r]?.let { TextRes(it) } ?: reasonWithNumbers(r, e) }

private fun reasonWithNumbers(r: Reason, e: Evaluation): TextRes? = when (r) {
    Reason.REST_PRELIM -> e.input.restMin?.let { TextRes(R.string.fork_cells_reason_rest_prelim, it) }
    Reason.COLD -> e.input.probesC.filter { it != NO_TEMP }.minOrNull()?.let { TextRes(R.string.fork_cells_reason_cold, it) }
    Reason.SPREAD_ABOVE_NORM -> TextRes(R.string.fork_cells_reason_above_norm, e.spreadMv, e.limits.normMv)
    Reason.SERVICE_TWICE -> serviceTwiceText(e)
    Reason.LAGGARD -> e.laggards.firstOrNull()?.let { TextRes(R.string.fork_cells_reason_laggard, it.cell, it.times, it.of) }
    Reason.BASE_GROWTH -> e.growthMv?.let { g -> e.baseMv?.let { TextRes(R.string.fork_cells_reason_base, g, it) } }
    Reason.LOW_WITH_LAGGARD -> TextRes(R.string.fork_cells_reason_low_laggard, e.lowestCell)
    else -> null
}

/** With the earlier spread when the engine knows it; a repeat under load has none. */
private fun serviceTwiceText(e: Evaluation): TextRes? {
    val svc = e.limits.serviceMv ?: return null
    val before = e.previousSpreadMv ?: return TextRes(R.string.fork_cells_reason_service_repeat, svc)
    return TextRes(R.string.fork_cells_reason_service_twice, svc, before)
}

/** The stream line of the window; null while it is fine or idle. NO_STREAM tells «stopped» ([framesSeen]) from «never came». */
fun streamText(s: StreamStatus, framesSeen: Boolean): TextRes? = when (s.state) {
    StreamState.IDLE, StreamState.OK -> null
    StreamState.CONNECTING -> TextRes(R.string.fork_cells_stream_connecting)
    StreamState.NO_STREAM -> TextRes(if (framesSeen) R.string.fork_cells_stream_stopped else R.string.fork_cells_stream_no_stream)
    StreamState.MISMATCH -> TextRes(R.string.fork_cells_stream_mismatch, s.reportedCells, s.reportedProbes)
    StreamState.HELPER_MISSING -> TextRes(R.string.fork_cells_stream_helper_missing)
    StreamState.HELPER_OUTDATED -> TextRes(R.string.fork_cells_stream_helper_outdated)
    StreamState.LISTEN_FAILED -> TextRes(R.string.fork_cells_stream_listen_failed, s.detail.ifBlank { "—" })
}

/** The one line under the header after «Снять замер сейчас»; null before it. */
fun manualText(m: ManualUi): TextRes? = when (m) {
    ManualUi.Idle -> null
    ManualUi.Taking -> TextRes(R.string.fork_cells_manual_taking)
    ManualUi.Error -> TextRes(R.string.fork_cells_manual_error)
    is ManualUi.Done -> when (val r = m.r) {
        is ManualResult.Stored ->
            if (r.conditionsOk || r.problems.isEmpty()) {
                TextRes(R.string.fork_cells_manual_ok)
            } else {
                TextRes(R.string.fork_cells_manual_bad, Joined(r.problems.map { TextRes(problemRes(it)) }))
            }
        is ManualResult.Failed -> TextRes(R.string.fork_cells_manual_failed, failText(r.why))
    }
}

private fun failText(why: StreamState): TextRes = TextRes(
    when (why) {
        StreamState.NO_STREAM -> R.string.fork_cells_entry_no_stream
        StreamState.MISMATCH -> R.string.fork_cells_entry_mismatch
        StreamState.HELPER_MISSING -> R.string.fork_cells_fail_helper
        StreamState.HELPER_OUTDATED -> R.string.fork_cells_fail_outdated
        StreamState.LISTEN_FAILED -> R.string.fork_cells_fail_listen
        StreamState.IDLE -> R.string.fork_cells_fail_no_car
        StreamState.CONNECTING, StreamState.OK -> R.string.fork_cells_fail_timeout
    },
)

/** One part of the footer's conditions line. */
fun partText(p: ConditionPart, locale: Locale): TextRes = when (p) {
    is ConditionPart.Kind -> TextRes(kindRes(p.k))
    is ConditionPart.Rest -> TextRes(R.string.fork_cells_part_rest, durationText(p.min))
    ConditionPart.RestUnknown -> TextRes(R.string.fork_cells_part_rest_unknown)
    is ConditionPart.Current -> TextRes(R.string.fork_cells_part_current, decimal(p.a, 1, locale))
    is ConditionPart.Probes -> TextRes(R.string.fork_cells_part_probes, rangeText(p.min, p.max))
}

/** «Замер после стоянки, покой 9 ч, ток 3,1 А, датчики 19–20 °C. Зона: плато 15–85 %, условия порога — покой ≥ 1 ч. …» */
fun footerText(e: Evaluation, locale: Locale): TextRes =
    TextRes(R.string.fork_cells_footer, Joined(conditionsLine(e).map { partText(it, locale) }), TextRes(zoneRes(e.row)), TextRes(condRes(e.row)))

/** The status after «Проверка батареи ·» on the «Машина» row; null for RESULT and HIDDEN (drawn apart). */
fun entryStatusText(row: EntryRow): TextRes? = when (row.kind) {
    EntryKind.COLLECT_OFF -> TextRes(R.string.fork_cells_entry_collect_off)
    EntryKind.NO_MEASUREMENTS -> TextRes(R.string.fork_cells_entry_empty)
    EntryKind.MISMATCH -> TextRes(R.string.fork_cells_entry_mismatch)
    EntryKind.NO_STREAM -> TextRes(if (row.stopped) R.string.fork_cells_entry_stopped else R.string.fork_cells_entry_no_stream)
    EntryKind.HIDDEN, EntryKind.RESULT -> null
}

/** «Сбор данных: вкл · снимок раз в 30 с» or «Сбор данных выключен». */
fun collectText(c: CellsConfig): TextRes =
    if (c.collect) TextRes(R.string.fork_cells_collect_on, TextRes(intervalLabelRes(c.intervalSec))) else TextRes(R.string.fork_cells_collect_off)
