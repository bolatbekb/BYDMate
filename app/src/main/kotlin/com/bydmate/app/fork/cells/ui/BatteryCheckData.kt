package com.bydmate.app.fork.cells.ui

import android.util.Log
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.SnapshotSummary
import com.bydmate.app.fork.cells.data.cellsDb
import com.bydmate.app.fork.cells.verdict.CellHistoryLoader
import com.bydmate.app.fork.cells.verdict.Evaluation
import com.bydmate.app.fork.cells.verdict.Level
import com.bydmate.app.fork.cells.verdict.SpreadPoint
import com.bydmate.app.fork.cells.verdict.toSpreadPoint
import java.util.concurrent.ConcurrentHashMap

/** What the window shows, read in one go: the picker, the default selection and the history ([historyKeys] for its levels). */
internal data class WindowData(
    val picker: List<PickerItem>,
    val selectedId: Long?,
    val selected: Evaluation?,
    val latestLoad: Evaluation?,
    val history: List<SpreadPoint>,
    val historyKeys: List<LevelKey> = emptyList(),
)

/**
 * What a stored measurement's level depends on: the measurement ([ts] guards against an id reused after «Очистить») and
 * its relax state, the one thing written after it is stored. The history before it never changes.
 */
internal data class LevelKey(val id: Long, val ts: Long, val relax: String)

internal data class LevelMark(val level: Level, val preliminary: Boolean)

/** The picker with its levels and the level of every measurement the window knows, by id. */
internal data class Levels(val picker: List<PickerItem>, val byId: Map<Long, Level>)

/**
 * The window's reads of the cells database, bounded: up to [REST_ITEMS] measurements of the picker
 * and [LOAD_ITEMS] loads, history of up to [HISTORY_POINTS] rest periods. Every query is a LIMIT
 * projection; a failure gives an empty value, never an exception (only cancellation passes).
 * Levels are cached by [LevelKey] for the view model's life: reopening the window evaluates only
 * what is new. Called on the IO dispatcher. fork-local.
 */
internal class BatteryCheckData(
    private val repo: CellRepository,
    private val loader: CellHistoryLoader,
    private val wall: () -> Long = System::currentTimeMillis,
) {
    private val cache = ConcurrentHashMap<LevelKey, LevelMark>()

    /** Default selection: [select] when given, else the latest rest measurement, else the newest of any kind. */
    suspend fun load(select: Long?): WindowData {
        val rest = quiet { repo.measurementsBetweenLimit(PICKER_KINDS, 0L, Long.MAX_VALUE, REST_ITEMS) }.orEmpty()
        val loads = quiet { repo.measurementsBetweenLimit(LOAD_KINDS, 0L, Long.MAX_VALUE, LOAD_ITEMS) }.orEmpty()
        val picker = (rest + loads).mapNotNull { pickerItemOf(it) }
        val id = select ?: quiet { loader.latestRestId() } ?: picker.maxByOrNull { it.ts }?.id
        val selected = id?.let { evaluate(it) }
        val latestLoad = loads.firstOrNull()?.id?.let { if (it == selected?.input?.id) selected else evaluate(it) }
        val history = history()
        return WindowData(picker, id, selected, latestLoad, history.map { it.toSpreadPoint() }, history.map { LevelKey(it.id, it.ts, it.relax) })
    }

    /** Levels of the picker and the history: the window's own evaluations and the cache first, each other one evaluated once. */
    suspend fun levels(d: WindowData): Levels {
        listOfNotNull(d.selected, d.latestLoad).forEach { e ->
            cache[LevelKey(e.input.id, e.input.ts, e.input.relax.name)] = LevelMark(e.level, e.preliminary)
        }
        val picker = d.picker.map { item ->
            val mark = mark(LevelKey(item.id, item.ts, item.relax))
            if (mark == null) item else item.copy(level = mark.level, preliminary = mark.preliminary)
        }
        val byId = picker.mapNotNull { p -> p.level?.let { p.id to it } }.toMap() +
            d.historyKeys.mapNotNull { k -> mark(k)?.let { k.id to it.level } }
        return Levels(picker, byId)
    }

    private suspend fun mark(key: LevelKey): LevelMark? =
        cache[key] ?: evaluate(key.id)?.let { e -> LevelMark(e.level, e.preliminary).also { cache[key] = it } }

    suspend fun evaluate(id: Long): Evaluation? = quiet { loader.evaluate(id, wall()) }

    /** Spread per rest period, the latest measurement of each, oldest first. */
    private suspend fun history(): List<SnapshotSummary> = quiet {
        repo.measurementsBetweenLimit(REST_KINDS, 0L, Long.MAX_VALUE, HISTORY_ROWS)
            .filter { it.restPeriod != null }
            .distinctBy { it.restPeriod }
            .take(HISTORY_POINTS)
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
