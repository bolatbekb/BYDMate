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
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
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
private val CHARGE_REST_MIN = 330..420 // the charge ends 5.5-7 h before the morning: a full equalization curve
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
        if (charged) night(morning, restMin, w, probes)
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

    /**
     * The night of a charge: the charge end (a charging snapshot, the wide spread of a fresh full charge, ~120 mV more with
     * the weak cell) and a plain snapshot every 15 minutes until the morning measurement, while the BMS brings the top
     * cells back down (an exponential decay, tau 1.5 h). No random draw: the rest of the history stays as it was.
     */
    private fun night(morning: Long, restMin: Int, w: Double, probes: IntArray) {
        val end = morning - restMin * MIN_MS
        val base = DemoCellModel.cellsMv(FULL_SOC.toDouble(), 0.0, w)
        val extra = (CHARGE_END_SPREAD_MV * (1 + w) - (base.max() - base.min())).coerceAtLeast(0.0)
        var t = end
        while (t < morning) {
            val h = (t - end) / HOUR_MS.toDouble()
            val charging = t == end
            add(t, balancing(base, extra * exp(-h / RELAX_TAU_H)), probes, FULL_SOC, if (charging) CHARGE_TAIL_A else 0.0, null, null, Relax.NOT_NEEDED, null, 0, charging)
            t += NIGHT_STEP_MS
        }
    }

    /** [base] with the cells above the median raised, the highest by [extraMv]: the top cells a balancing BMS discharges. */
    private fun balancing(base: IntArray, extraMv: Double): IntArray {
        val real = DemoCellModel.REAL_CELLS_MV
        val med = real.sorted()[real.size / 2]
        val top = (real.max() - med).coerceAtLeast(1)
        return IntArray(base.size) { i -> base[i] + (extraMv * (real[i] - med).coerceAtLeast(0) / top).roundToInt() }
    }

    @Suppress("LongParameterList") // why: one row builder, every column named at the call
    private fun add(
        ts: Long, cells: IntArray, probes: IntArray, soc: Int, amps: Double,
        kind: MeasureKind?, period: Long?, relax: Relax, restMin: Int?, sinceFull: Int, charging: Boolean = false,
    ) {
        out += CellSnapshotEntity(
            ts = ts, elapsedMs = ts - ts % MIN_MS, profile = CarProfile.LEOPARD5_318.id, measure = kind?.name,
            restPeriod = period, relax = relax.name, relaxDropMv = null, soc = soc, currentA = amps,
            packV = cells.sum() / MV_PER_V, charging = charging, restMin = restMin, daysSinceFull = sinceFull,
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
        const val HOUR_MS = 3_600_000L
        const val NIGHT_STEP_MS = 15 * MIN_MS
        const val CHARGE_END_SPREAD_MV = 120.0
        const val RELAX_TAU_H = 1.5
        const val CHARGE_TAIL_A = -3.0
    }
}
