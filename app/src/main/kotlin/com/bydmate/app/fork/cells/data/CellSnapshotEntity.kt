package com.bydmate.app.fork.cells.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Suppress("ArrayInDataClass") // why: Room entity; the blobs are never compared
@Entity(tableName = "cell_snapshots", indices = [Index("ts"), Index("measure")])
data class CellSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    @ColumnInfo(name = "elapsed_ms") val elapsedMs: Long,
    val profile: String,
    val measure: String?,
    @ColumnInfo(name = "rest_period") val restPeriod: Long?,
    val relax: String,
    @ColumnInfo(name = "relax_drop_mv") val relaxDropMv: Int?,
    val soc: Int?,
    @ColumnInfo(name = "current_a") val currentA: Double?,
    @ColumnInfo(name = "pack_v") val packV: Int?,
    val charging: Boolean,
    @ColumnInfo(name = "rest_min") val restMin: Int?,
    @ColumnInfo(name = "days_since_full") val daysSinceFull: Int?,
    @ColumnInfo(name = "vmin_mv") val vminMv: Int,
    @ColumnInfo(name = "vmax_mv") val vmaxMv: Int,
    @ColumnInfo(name = "spread_mv") val spreadMv: Int,
    @ColumnInfo(name = "probe_min_c") val probeMinC: Int?,
    @ColumnInfo(name = "probe_max_c") val probeMaxC: Int?,
    @ColumnInfo(name = "flag_consistency") val flagConsistency: Int?,
    @ColumnInfo(name = "flag_temp_diff") val flagTempDiff: Int?,
    @ColumnInfo(name = "flag_unbalanced") val flagUnbalanced: Int?,
    val cells: ByteArray,
    val probes: ByteArray,
)

/** List projection: no blobs. */
data class SnapshotSummary(
    val id: Long,
    val ts: Long,
    val measure: String?,
    val restPeriod: Long?,
    val relax: String,
    val soc: Int?,
    val currentA: Double?,
    val charging: Boolean,
    val restMin: Int?,
    val daysSinceFull: Int?,
    val vmaxMv: Int,
    val vminMv: Int,
    val spreadMv: Int,
    val probeMinC: Int?,
    val probeMaxC: Int?,
)

data class AnchorRow(val ts: Long, val elapsedMs: Long, val soc: Int?)

data class CellStatsRow(val snapshots: Int, val firstTs: Long?, val rest: Int, val afterCharge: Int, val load: Int)

data class CellStats(val row: CellStatsRow, val bytes: Long)
