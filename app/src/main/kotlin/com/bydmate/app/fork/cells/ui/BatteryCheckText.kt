package com.bydmate.app.fork.cells.ui

import com.bydmate.app.R
import com.bydmate.app.fork.cells.collect.ManualProblem
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.verdict.CellClass
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.Origin
import com.bydmate.app.fork.cells.verdict.ZoneRow

// Plain string resources of the battery-check window. fork-local.

fun kindRes(k: MeasureKind): Int = when (k) {
    MeasureKind.AFTER_PARKING -> R.string.fork_cells_kind_after_parking
    MeasureKind.REST_IN_SESSION -> R.string.fork_cells_kind_rest
    MeasureKind.AFTER_CHARGE -> R.string.fork_cells_kind_after_charge
    MeasureKind.LOAD -> R.string.fork_cells_kind_load
    MeasureKind.MANUAL -> R.string.fork_cells_kind_manual
}

fun levelRes(l: Level): Int = when (l) {
    Level.NONE -> R.string.fork_cells_level_none
    Level.OK -> R.string.fork_cells_level_ok
    Level.WATCH -> R.string.fork_cells_level_watch
    Level.SERVICE -> R.string.fork_cells_level_service
}

/** The verdict in plain words; at the top after a charge «ok» says the cells equalise as they should. */
fun titleRes(e: Evaluation): Int = when (e.level) {
    Level.NONE -> R.string.fork_cells_title_none
    Level.OK -> if (e.input.kind == MeasureKind.AFTER_CHARGE || e.row == ZoneRow.TOP) R.string.fork_cells_title_ok_charge else R.string.fork_cells_title_ok
    Level.WATCH -> R.string.fork_cells_title_watch
    Level.SERVICE -> R.string.fork_cells_title_service
}

fun zoneRes(z: ZoneRow): Int = when (z) {
    ZoneRow.PLATEAU -> R.string.fork_cells_zone_plateau
    ZoneRow.TOP_KNEE -> R.string.fork_cells_zone_knee
    ZoneRow.TOP -> R.string.fork_cells_zone_top
    ZoneRow.LOW_10_15 -> R.string.fork_cells_zone_low
    ZoneRow.LOW_UNDER_10 -> R.string.fork_cells_zone_low10
    ZoneRow.LOAD -> R.string.fork_cells_zone_load
}

/** The measuring condition the zone's threshold needs. */
fun condRes(z: ZoneRow): Int = when (z) {
    ZoneRow.TOP -> R.string.fork_cells_cond_after_full
    ZoneRow.LOAD -> R.string.fork_cells_cond_load
    else -> R.string.fork_cells_cond_rest
}

fun originRes(o: Origin): Int = when (o) {
    Origin.BYD -> R.string.fork_cells_origin_byd
    Origin.STANDARD -> R.string.fork_cells_origin_standard
    Origin.MEASURED -> R.string.fork_cells_origin_measured
    Origin.COMMUNITY -> R.string.fork_cells_origin_community
    Origin.INFERENCE -> R.string.fork_cells_origin_inference
}

fun problemRes(p: ManualProblem): Int = when (p) {
    ManualProblem.CURRENT -> R.string.fork_cells_problem_current
    ManualProblem.SHORT_REST -> R.string.fork_cells_problem_short_rest
    ManualProblem.UNKNOWN_REST -> R.string.fork_cells_problem_unknown_rest
    ManualProblem.CHARGING -> R.string.fork_cells_problem_charging
    ManualProblem.COLD -> R.string.fork_cells_problem_cold
}

fun groupRes(c: CellClass): Int = when (c) {
    CellClass.STRONGLY_LOW -> R.string.fork_cells_group_strong_low
    CellClass.NOTICEABLY_LOW -> R.string.fork_cells_group_low
    CellClass.NOTICEABLY_HIGH -> R.string.fork_cells_group_high
    CellClass.STRONGLY_HIGH -> R.string.fork_cells_group_strong_high
    CellClass.NORMAL -> R.string.fork_cells_legend_normal
}

fun tabRes(t: DetailsTab): Int = when (t) {
    DetailsTab.DEVIATIONS -> R.string.fork_cells_tab_deviations
    DetailsTab.PROBES -> R.string.fork_cells_tab_probes
    DetailsTab.HISTORY -> R.string.fork_cells_tab_history
    DetailsTab.MEASURES -> R.string.fork_cells_tab_measures
}

/** The texts of one help-table row: condition, norm, watch, service. */
data class HelpRowRes(val cond: Int, val norm: Int, val watch: Int, val service: Int)

/** By [com.bydmate.app.fork.cells.verdict.ThresholdRow.conditionKey]; null for a key the table does not know. */
fun helpRow(key: String): HelpRowRes? = when (key) {
    "plateau" -> HelpRowRes(
        R.string.fork_cells_help_row_plateau_cond, R.string.fork_cells_help_row_plateau_norm,
        R.string.fork_cells_help_row_plateau_watch, R.string.fork_cells_help_row_plateau_service,
    )
    "top_knee" -> HelpRowRes(
        R.string.fork_cells_help_row_top_knee_cond, R.string.fork_cells_help_row_top_knee_norm,
        R.string.fork_cells_help_row_top_knee_watch, R.string.fork_cells_help_row_top_knee_service,
    )
    "top" -> HelpRowRes(
        R.string.fork_cells_help_row_top_cond, R.string.fork_cells_help_row_top_norm,
        R.string.fork_cells_help_row_top_watch, R.string.fork_cells_help_row_top_service,
    )
    "low_10_15" -> HelpRowRes(
        R.string.fork_cells_help_row_low_10_15_cond, R.string.fork_cells_help_row_low_10_15_norm,
        R.string.fork_cells_help_row_low_10_15_watch, R.string.fork_cells_help_row_low_10_15_service,
    )
    "low_under_10" -> HelpRowRes(
        R.string.fork_cells_help_row_low_under_10_cond, R.string.fork_cells_help_row_low_under_10_norm,
        R.string.fork_cells_help_row_low_under_10_watch, R.string.fork_cells_help_row_low_under_10_service,
    )
    "load" -> HelpRowRes(
        R.string.fork_cells_help_row_load_cond, R.string.fork_cells_help_row_load_norm,
        R.string.fork_cells_help_row_load_watch, R.string.fork_cells_help_row_load_service,
    )
    "bms_flag" -> HelpRowRes(
        R.string.fork_cells_help_row_bms_flag_cond, R.string.fork_cells_help_row_bms_flag_norm,
        R.string.fork_cells_help_row_bms_flag_watch, R.string.fork_cells_help_row_bms_flag_service,
    )
    else -> null
}
