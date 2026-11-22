package com.bydmate.app.fork.cells.data

import android.util.Log
import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.collect.RelaxUpdate
import com.bydmate.app.fork.cells.collect.SessionAnchor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File

/** Guarded access to the cells database: a failing DB yields null / empty / false, never an exception. */
@Suppress("TooManyFunctions") // why: one guarded facade over the DAO, API fixed by the plan
class CellRepository(private val dao: CellDao, private val dbFile: () -> File) {

    suspend fun insert(e: CellSnapshotEntity): Long? = guard("insert") { dao.insert(e) }

    suspend fun updateRelax(u: RelaxUpdate) {
        guard("updateRelax") { dao.updateRelax(u.measurementId, u.relax.name, u.dropMv) }
    }

    /** The last stored row as the session anchor; null without rows or when the last row is older than [clearedAtMs]. */
    suspend fun anchor(clearedAtMs: Long): SessionAnchor? = guard("anchor") {
        dao.lastAnchor()?.takeIf { it.ts >= clearedAtMs }?.let { SessionAnchor(it.ts, it.elapsedMs, it.soc) }
    }

    fun measurements(limit: Int = DEFAULT_LIMIT): Flow<List<SnapshotSummary>> =
        dao.measurements(limit).catch { fail("measurements", it); emit(emptyList()) }

    suspend fun entity(id: Long): CellSnapshotEntity? = guard("entity") { dao.byId(id) }

    suspend fun entities(ids: List<Long>): List<CellSnapshotEntity> =
        guard("entities") { ids.chunked(IN_CHUNK).flatMap { dao.byIds(it) } } ?: emptyList()

    suspend fun measurementsBetween(kinds: Set<MeasureKind>, from: Long, to: Long): List<SnapshotSummary> =
        guard("measurementsBetween") { dao.measurementsBetween(kinds.map { it.name }, from, to) } ?: emptyList()

    suspend fun measurementsBetweenLimit(kinds: Set<MeasureKind>, from: Long, to: Long, limit: Int): List<SnapshotSummary> =
        guard("measurementsBetweenLimit") { dao.measurementsBetweenLimit(kinds.map { it.name }, from, to, limit) } ?: emptyList()

    suspend fun latestRestId(): Long? = guard("latestRestId") { dao.latestRestId() }

    suspend fun latestLoadId(): Long? = guard("latestLoadId") { dao.latestLoadId() }

    suspend fun summariesBetween(from: Long, to: Long): List<SnapshotSummary> =
        guard("summariesBetween") { dao.summariesBetween(from, to) } ?: emptyList()

    fun stats(): Flow<CellStats> = dao.stats()
        .map { CellStats(it, fileBytes()) }
        .catch { fail("stats", it); emit(CellStats(CellStatsRow(0, null, 0, 0, 0), 0L)) }

    suspend fun exportCsv(dir: File, nowWallMs: Long): File? = guard("exportCsv") { CellCsv.write(dao, dir, nowWallMs) }

    suspend fun clear(): Boolean = guard("clear") { dao.clear() } != null

    private fun fileBytes(): Long = runCatching {
        val f = dbFile()
        f.length() + File(f.path + "-wal").length()
    }.getOrDefault(0L)

    private suspend inline fun <T> guard(op: String, crossinline block: suspend () -> T): T? {
        var result: T? = null
        val ok = cellsDb({ fail(op, it) }) { result = block() }
        return if (ok == null) null else result
    }

    private fun fail(op: String, t: Throwable) {
        runCatching { Log.w(TAG, "cells db $op failed: ${t.javaClass.simpleName}") }
    }

    companion object {
        const val DEFAULT_LIMIT = 60
        private const val IN_CHUNK = 500
        private const val TAG = "CellRepository"
    }
}
