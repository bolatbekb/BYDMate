package com.bydmate.app.fork.cells.ui.views

import com.bydmate.app.R
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.ui.TextRes
import com.bydmate.app.fork.cells.ui.reasonTexts
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.ZoneRow
import com.bydmate.app.ui.tech.DASH

// String resources of the four views; texts with a percent sign and no argument go through resolve() (TextRes.text()). fork-local.

fun adviceTitleRes(a: Advice): Int = when (a) {
    Advice.NOTHING_TO_DO -> R.string.fork_cells_wtd_title_nothing
    Advice.WATCH -> R.string.fork_cells_wtd_title_watch
    Advice.SERVICE -> R.string.fork_cells_wtd_title_service
    Advice.NO_VERDICT -> R.string.fork_cells_wtd_title_none
    Advice.WAIT_RELAX -> R.string.fork_cells_wtd_title_wait
    Advice.PRELIMINARY -> R.string.fork_cells_wtd_title_prelim
}

fun adviceBodyRes(a: Advice): Int = when (a) {
    Advice.NOTHING_TO_DO -> R.string.fork_cells_wtd_body_nothing
    Advice.WATCH -> R.string.fork_cells_wtd_body_watch
    Advice.SERVICE -> R.string.fork_cells_wtd_body_service
    Advice.NO_VERDICT -> R.string.fork_cells_wtd_body_none
    Advice.WAIT_RELAX -> R.string.fork_cells_wtd_body_wait
    Advice.PRELIMINARY -> R.string.fork_cells_wtd_body_prelim
}

/** The title and the hint of a recheck row. */
fun recheckRes(r: Recheck): Pair<Int, Int> = when (r) {
    Recheck.AFTER_CHARGE_HOUR -> R.string.fork_cells_wtd_re_charge to R.string.fork_cells_wtd_re_charge_hint
    Recheck.AFTER_PARKING_HOUR -> R.string.fork_cells_wtd_re_parking to R.string.fork_cells_wtd_re_parking_hint
    Recheck.ON_PLATEAU -> R.string.fork_cells_wtd_re_plateau to R.string.fork_cells_wtd_re_plateau_hint
    Recheck.AFTER_FULL_AC -> R.string.fork_cells_wtd_re_full to R.string.fork_cells_wtd_re_full_hint
    Recheck.WARM -> R.string.fork_cells_wtd_re_warm to R.string.fork_cells_wtd_re_warm_hint
    Recheck.MONTHLY -> R.string.fork_cells_wtd_re_monthly to R.string.fork_cells_wtd_re_monthly_hint
}

/** By [NormRow.labelKey]. */
fun normLabelRes(key: String): Int = when (key) {
    "low_under_10" -> R.string.fork_cells_wtd_row_low10
    "low_10_15" -> R.string.fork_cells_wtd_row_low
    "plateau" -> R.string.fork_cells_wtd_row_plateau
    "top_knee" -> R.string.fork_cells_wtd_row_knee
    else -> R.string.fork_cells_wtd_row_top
}

/** «Почему так»: why the zone has its norm. */
fun whyRes(z: ZoneRow): Int = when (z) {
    ZoneRow.PLATEAU -> R.string.fork_cells_norm_why_plateau
    ZoneRow.TOP_KNEE -> R.string.fork_cells_norm_why_knee
    ZoneRow.TOP -> R.string.fork_cells_norm_why_top
    ZoneRow.LOW_10_15 -> R.string.fork_cells_norm_why_low
    ZoneRow.LOW_UNDER_10 -> R.string.fork_cells_norm_why_low10
    ZoneRow.LOAD -> R.string.fork_cells_norm_why_load
}

/** Where the zone's thresholds come from (the help table's origins, in words). */
fun sourceRes(z: ZoneRow): Int = when (z) {
    ZoneRow.PLATEAU -> R.string.fork_cells_norm_src_plateau
    ZoneRow.TOP_KNEE -> R.string.fork_cells_norm_src_knee
    ZoneRow.TOP -> R.string.fork_cells_norm_src_top
    ZoneRow.LOW_10_15 -> R.string.fork_cells_norm_src_low
    ZoneRow.LOW_UNDER_10 -> R.string.fork_cells_norm_src_low10
    ZoneRow.LOAD -> R.string.fork_cells_norm_src_load
}

/** «56 %», or a dash when the SOC is unknown. */
fun socArg(soc: Int?): Any = soc?.let { TextRes(R.string.fork_cells_val_percent, it) } ?: DASH

/** Under the verdict title: for «всё отлично» the norm against the spread and one reason; otherwise the first two reasons. */
fun summaryTexts(e: Evaluation): List<TextRes> {
    val reasons = reasonTexts(e)
    if (e.level != Level.OK) return reasons.take(2)
    val ok = when {
        e.row == ZoneRow.LOAD -> TextRes(R.string.fork_cells_norm_ok_load, e.limits.normMv, e.spreadMv)
        e.row == ZoneRow.TOP || e.input.kind == MeasureKind.AFTER_CHARGE -> TextRes(R.string.fork_cells_norm_ok_charge, e.limits.normMv, e.spreadMv)
        else -> TextRes(R.string.fork_cells_norm_ok_text, socArg(e.input.soc), e.limits.normMv, e.spreadMv)
    }
    return listOf(ok) + reasons.take(1)
}

/** «в норме» / «выше нормы» / «рано судить» after «У вас: N мВ —». */
fun yoursRes(advice: Advice, e: Evaluation?): Int = when {
    e == null || advice == Advice.NO_VERDICT || advice == Advice.WAIT_RELAX -> R.string.fork_cells_wtd_yours_early
    e.spreadMv <= e.limits.normMv -> R.string.fork_cells_wtd_yours_ok
    else -> R.string.fork_cells_wtd_yours_above
}
