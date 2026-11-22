package com.bydmate.app.fork.cells.ui

import android.util.Log
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.cellsDb
import com.bydmate.app.fork.cells.verdict.CellHistoryLoader
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.SpreadPoint
import com.bydmate.app.fork.cells.verdict.toSpreadPoint

/** What the window shows, read in one go: the picker, the default selection and the history. */
internal data class WindowData(
    val picker: List<PickerItem>,
    val selectedId: Long?,
    val selected: Evaluation?,
    val latestLoad: Evaluation?,
    val history: List<SpreadPoint>,
)

/**
 * The window's reads of the cells database, bounded: up to [REST_ITEMS] measurements of the picker
 * and [LOAD_ITEMS] loads, history of up to [HISTORY_POINTS] rest periods. Every query is a LIMIT
 * projection; a failure gives an empty value, never an exception (only cancellation passes).
 * Called on the IO dispatcher. fork-local.
 */
internal class BatteryCheckData(
    private val repo: CellRepository,
    private val loader: CellHistoryLoader,
    private val wall: () -> Long = System::currentTimeMillis,
) {
    /** Default selection: [select] when given, else the latest rest measurement, else the newest of any kind. */
    suspend fun load(select: Long?): WindowData {
        val rest = quiet { repo.measurementsBetweenLimit(PICKER_KINDS, 0L, Long.MAX_VALUE, REST_ITEMS) }.orEmpty()
        val loads = quiet { repo.measurementsBetweenLimit(LOAD_KINDS, 0L, Long.MAX_VALUE, LOAD_ITEMS) }.orEmpty()
        val picker = (rest + loads).mapNotNull { pickerItemOf(it) }
        val id = select ?: quiet { loader.latestRestId() } ?: picker.maxByOrNull { it.ts }?.id
        val selected = id?.let { evaluate(it) }
        val latestLoad = loads.firstOrNull()?.id?.let { if (it == selected?.input?.id) selected else evaluate(it) }
        return WindowData(picker, id, selected, latestLoad, history())
    }

    /** The picker with the level of each item; the ones already evaluated are reused. */
    suspend fun withLevels(picker: List<PickerItem>, known: List<Evaluation?>): List<PickerItem> {
        val byId = known.filterNotNull().associateBy { it.input.id }
        return picker.map { item ->
            val e = byId[item.id] ?: evaluate(item.id)
            if (e == null) item else item.copy(level = e.level, preliminary = e.preliminary)
        }
    }

    suspend fun evaluate(id: Long): Evaluation? = quiet { loader.evaluate(id, wall()) }

    /** The evaluation for the «Машина» row: the latest rest measurement, else [newestId]. */
    suspend fun latest(newestId: Long): Evaluation? = evaluate(quiet { loader.latestRestId() } ?: newestId)

    /** Spread per rest period, the latest measurement of each, oldest first. */
    private suspend fun history(): List<SpreadPoint> = quiet {
        repo.measurementsBetweenLimit(REST_KINDS, 0L, Long.MAX_VALUE, HISTORY_ROWS)
            .filter { it.restPeriod != null }
            .distinctBy { it.restPeriod }
            .take(HISTORY_POINTS)
            .map { it.toSpreadPoint() }
            .reversed()
    }.orEmpty()

    private inline fun <T> quiet(block: () -> T): T? =
        cellsDb({ t -> runCatching { Log.w(TAG, "battery check read failed: ${t.javaClass.simpleName}") } }, block)

    companion object {
        const val REST_ITEMS = 30
        const val LOAD_ITEMS = 6
        const val HISTORY_POINTS = 30

        /** Rows read for the history: a rest period may hold two measurements (R8), so twice the points and some. */
        private const val HISTORY_ROWS = 90
        private const val TAG = "BatteryCheck"
        private val REST_KINDS = setOf(MeasureKind.AFTER_PARKING, MeasureKind.REST_IN_SESSION, MeasureKind.AFTER_CHARGE, MeasureKind.MANUAL)
        private val PICKER_KINDS = REST_KINDS
        private val LOAD_KINDS = setOf(MeasureKind.LOAD)
    }
}
