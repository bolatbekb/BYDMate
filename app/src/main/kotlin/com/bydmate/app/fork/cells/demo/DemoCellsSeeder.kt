package com.bydmate.app.fork.cells.demo

import com.bydmate.app.fork.cells.collect.MeasureKind
import com.bydmate.app.fork.cells.collect.Relax
import com.bydmate.app.fork.cells.data.CarProfile
import com.bydmate.app.fork.cells.data.CellBlob
import com.bydmate.app.fork.cells.data.CellRepository
import com.bydmate.app.fork.cells.data.CellSnapshotEntity
import com.bydmate.app.fork.cells.data.CellsSettings
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow
import kotlin.random.Random

private const val DAYS = 90
private const val SEED = 42
private const val MORNING_H = 7
private const val MORNING_MIN = 30
private const val EVENING_H = 21
private const val CHARGE_EVERY = 3
private const val COMMUTE_SNAPSHOTS = 6
private const val MIN_MS = 60_000L
private const val BATCH = 100
private val PARK_REST_MIN = 540..660
private val CHARGE_REST_MIN = 240..420
private const val WEAKNESS_EXPONENT = 1.5
private const val PRESET_SOC_MIN = 40
private const val PRESET_SOC_MAX = 80

/** 90 days of a demo history: a morning rest every day, a charge every third night, one cell that weakens slowly. */
object DemoCellsSeeder {
    /** Rows inserted; 0 when the database is not empty, or when anything fails. Never throws. */
    suspend fun seedIfEmpty(
        repo: CellRepository,
        settings: CellsSettings,
        nowWallMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int = runCatching {
        if (repo.latestRestId() != null || repo.latestLoadId() != null) return 0
        val rows = DemoHistory(zone, nowWallMs).build()
        val inserted = rows.list.chunked(BATCH).sumOf { chunk -> chunk.count { repo.insert(it) != null } }
        if (rows.lastFullMs > 0L) settings.lastFull100WallMs = rows.lastFullMs
        inserted
    }.getOrDefault(0)
}

private class History(val list: List<CellSnapshotEntity>, val lastFullMs: Long)

private class DemoHistory(private val zone: ZoneId, private val nowWallMs: Long) {
    private val rnd = Random(SEED)
    private val out = ArrayList<CellSnapshotEntity>()
    private var lastFull = 0L

    fun build(): History {
        val today = java.time.Instant.ofEpochMilli(nowWallMs).atZone(zone).toLocalDate()
        var fullDay = 0
        for (d in 0 until DAYS) {
            val date = today.minusDays((DAYS - d).toLong())
            val w = (d / (DAYS - 1).toDouble()).pow(WEAKNESS_EXPONENT)
            val charged = d % CHARGE_EVERY == 0 && d != DAYS - 1
            if (charged) fullDay = d
            day(date, d, w, charged, d - fullDay)
        }
        return History(out.sortedBy { it.ts }, lastFull)
    }

    private fun at(date: LocalDate, h: Int, m: Int): Long = date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun day(date: LocalDate, d: Int, w: Double, charged: Boolean, sinceFull: Int) {
        val morning = at(date, MORNING_H, MORNING_MIN)
        val period = at(date.minusDays(1), EVENING_H, 0)
        val avg = if (d in COLD_DAYS) COLD_C else rnd.nextInt(WARM_MIN_C, WARM_MAX_C + 1)
        val probes = DemoCellModel.probesC(avg)
        var soc = if (charged) FULL_SOC else rnd.nextInt(PRESET_SOC_MIN, PRESET_SOC_MAX + 1)
        val restMin = if (charged) rnd.nextInt(CHARGE_REST_MIN.first, CHARGE_REST_MIN.last + 1) else rnd.nextInt(PARK_REST_MIN.first, PARK_REST_MIN.last + 1)
        val kind = if (charged) MeasureKind.AFTER_CHARGE else MeasureKind.AFTER_PARKING
        val relax = if (charged) Relax.SETTLED else Relax.NOT_NEEDED
        add(morning, DemoCellModel.cellsMv(soc.toDouble(), 0.0, w), probes, soc, 0.0, kind, period, relax, restMin, if (charged) 0 else sinceFull)
        if (charged) lastFull = morning
        for (i in 0 until COMMUTE_SNAPSHOTS) {
            val t = at(date, if (i < COMMUTE_SNAPSHOTS / 2) COMMUTE_AM else COMMUTE_PM, i * COMMUTE_STEP_MIN)
            soc = (soc - 1).coerceAtLeast(MIN_SOC)
            val amps = COMMUTE_A.first + rnd.nextDouble() * (COMMUTE_A.last - COMMUTE_A.first)
            add(t, DemoCellModel.cellsMv(soc.toDouble(), amps, w), probes, soc, amps, null, null, Relax.NOT_NEEDED, null, sinceFull)
        }
        if (d % 2 == 0) {
            val amps = LOAD_A.first + rnd.nextDouble() * (LOAD_A.last - LOAD_A.first)
            val s = rnd.nextInt(LOAD_SOC.first, LOAD_SOC.last + 1)
            add(at(date, LOAD_H, 0), DemoCellModel.cellsMv(s.toDouble(), amps, w), probes, s, amps, MeasureKind.LOAD, null, Relax.NOT_NEEDED, null, sinceFull)
        }
    }

    @Suppress("LongParameterList") // why: one row builder, every column named at the call
    private fun add(
        ts: Long, cells: IntArray, probes: IntArray, soc: Int, amps: Double,
        kind: MeasureKind?, period: Long?, relax: Relax, restMin: Int?, sinceFull: Int,
    ) {
        out += CellSnapshotEntity(
            ts = ts, elapsedMs = ts - ts % MIN_MS, profile = CarProfile.LEOPARD5_318.id, measure = kind?.name,
            restPeriod = period, relax = relax.name, relaxDropMv = null, soc = soc, currentA = amps,
            packV = cells.sum() / MV_PER_V, charging = false, restMin = restMin, daysSinceFull = sinceFull,
            vminMv = cells.min(), vmaxMv = cells.max(), spreadMv = cells.max() - cells.min(),
            probeMinC = probes.min(), probeMaxC = probes.max(), flagConsistency = null, flagTempDiff = null,
            flagUnbalanced = null, cells = CellBlob.encodeCells(cells), probes = CellBlob.encodeProbes(probes),
        )
    }

    private companion object {
        val COLD_DAYS = 5..8
        const val COLD_C = 8
        const val WARM_MIN_C = 18
        const val WARM_MAX_C = 22
        const val FULL_SOC = 100
        const val MIN_SOC = 5
        const val COMMUTE_AM = 8
        const val COMMUTE_PM = 17
        const val COMMUTE_STEP_MIN = 10
        val COMMUTE_A = 10..40
        val LOAD_A = 60..90
        val LOAD_SOC = 50..70
        const val LOAD_H = 17
        const val MV_PER_V = 1000
    }
}
