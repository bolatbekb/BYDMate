package com.bydmate.app.fork.cells.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

internal const val SUMMARY = "id, ts, measure, rest_period AS restPeriod, relax, soc, current_a AS currentA, charging, " +
    "rest_min AS restMin, days_since_full AS daysSinceFull, vmax_mv AS vmaxMv, vmin_mv AS vminMv, " +
    "spread_mv AS spreadMv, probe_min_c AS probeMinC, probe_max_c AS probeMaxC"

internal const val STATS = "SELECT COUNT(*) AS snapshots, MIN(ts) AS firstTs, " +
    "IFNULL(SUM(measure IN ('AFTER_PARKING','REST_IN_SESSION') OR (measure = 'MANUAL' AND rest_period IS NOT NULL)), 0) AS rest, " +
    "IFNULL(SUM(measure = 'AFTER_CHARGE'), 0) AS afterCharge, IFNULL(SUM(measure = 'LOAD'), 0) AS load FROM cell_snapshots"

@Suppress("TooManyFunctions") // why: one DAO of the cells table, API fixed by the plan
@Dao
interface CellDao {
    @Insert suspend fun insert(e: CellSnapshotEntity): Long

    @Query("UPDATE cell_snapshots SET relax = :relax, relax_drop_mv = :dropMv WHERE id = :id")
    suspend fun updateRelax(id: Long, relax: String, dropMv: Int?)

    @Query("SELECT ts, elapsed_ms AS elapsedMs, soc FROM cell_snapshots ORDER BY ts DESC LIMIT 1")
    suspend fun lastAnchor(): AnchorRow?

    @Query("SELECT * FROM cell_snapshots WHERE id = :id")
    suspend fun byId(id: Long): CellSnapshotEntity?

    @Query("SELECT * FROM cell_snapshots WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<CellSnapshotEntity>

    @Query("SELECT $SUMMARY FROM cell_snapshots WHERE measure IS NOT NULL ORDER BY ts DESC LIMIT :limit")
    fun measurements(limit: Int): Flow<List<SnapshotSummary>>

    @Query("SELECT $SUMMARY FROM cell_snapshots WHERE measure IN (:kinds) AND ts BETWEEN :from AND :to ORDER BY ts DESC")
    suspend fun measurementsBetween(kinds: List<String>, from: Long, to: Long): List<SnapshotSummary>

    @Query("SELECT $SUMMARY FROM cell_snapshots WHERE measure IN (:kinds) AND ts BETWEEN :from AND :to ORDER BY ts DESC LIMIT :limit")
    suspend fun measurementsBetweenLimit(kinds: List<String>, from: Long, to: Long, limit: Int): List<SnapshotSummary>

    @Query("SELECT id FROM cell_snapshots WHERE measure IS NOT NULL AND measure != 'LOAD' AND rest_period IS NOT NULL ORDER BY ts DESC LIMIT 1")
    suspend fun latestRestId(): Long?

    @Query("SELECT id FROM cell_snapshots WHERE measure = 'LOAD' ORDER BY ts DESC LIMIT 1")
    suspend fun latestLoadId(): Long?

    @Query("SELECT $SUMMARY FROM cell_snapshots WHERE ts BETWEEN :from AND :to ORDER BY ts")
    suspend fun summariesBetween(from: Long, to: Long): List<SnapshotSummary>

    @Query(STATS)
    fun stats(): Flow<CellStatsRow>

    @Query("SELECT * FROM cell_snapshots WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun page(afterId: Long, limit: Int): List<CellSnapshotEntity>

    @Query("DELETE FROM cell_snapshots")
    suspend fun clear()
}
