package com.bydmate.app.fork.cells.ui

import com.bydmate.app.R
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CheckView
import java.util.Locale

/** Rows of the settings block «Диагностика батареи». fork-local. */
enum class BlockRow { CAR, COLLECT, INTERVAL, VIEW, STATS }

/**
 * The battery-check data rows. The car model is no longer chosen here (it moved to «Мой автомобиль»),
 * so the CAR row is never shown and collection no longer depends on a profile — the four data rows
 * always appear; collection itself is gated by the COLLECT toggle alone.
 */
fun visibleRows(): List<BlockRow> =
    listOf(BlockRow.COLLECT, BlockRow.INTERVAL, BlockRow.VIEW, BlockRow.STATS)

fun intervalLabelRes(sec: Int): Int = when (sec) {
    10 -> R.string.fork_cells_interval_10s
    60 -> R.string.fork_cells_interval_1m
    300 -> R.string.fork_cells_interval_5m
    else -> R.string.fork_cells_interval_30s
}

fun viewLabelRes(v: CheckView): Int = when (v) {
    CheckView.WHAT_TO_DO -> R.string.fork_cells_view_what_to_do
    CheckView.NORM_BY_CHARGE -> R.string.fork_cells_view_norm
    CheckView.RULER -> R.string.fork_cells_view_ruler
    CheckView.EQUALIZATION -> R.string.fork_cells_view_equalization
}

fun profileLabelRes(p: CarProfile): Int = when (p) {
    CarProfile.NONE -> R.string.fork_cells_car_none
    CarProfile.LEOPARD5_318 -> R.string.fork_cells_car_leopard5
}

/** Storage size with one decimal in megabytes, in the locale's own format and unit text. */
fun sizeText(bytes: Long, locale: Locale): String {
    val unit = if (locale.language == "ru" || locale.language == "be") "МБ" else "MB"
    return String.format(locale, "%.1f %s", bytes / BYTES_PER_MB, unit)
}

private const val BYTES_PER_MB = 1024.0 * 1024.0
