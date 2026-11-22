package com.bydmate.app.fork.cells.data

import com.bydmate.app.fork.cells.stream.NO_TEMP
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CSV like the owner's cells CSV: `;` separator, decimal comma, empty field for null, UTF-8 with BOM (R25). */
object CellCsv {
    const val PAGE = 500
    const val BOM = "﻿"
    private val FIXED = listOf(
        "time", "measure", "soc", "current_a", "pack_v", "charging", "rest_min", "days_since_full", "relax",
        "relax_drop_mv", "spread_mv", "probe_min_c", "probe_max_c", "flag_consistency", "flag_temp_diff", "flag_unbalanced",
    )

    fun header(cells: Int, probes: Int): String =
        (FIXED + (1..cells).map { "c$it" } + (1..probes).map { "p$it" }).joinToString(";")

    fun line(e: CellSnapshotEntity): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(e.ts))
        val head = listOf(
            time, e.measure, e.soc, e.currentA?.let(::decimal), e.packV, if (e.charging) 1 else 0, e.restMin,
            e.daysSinceFull, e.relax, e.relaxDropMv, e.spreadMv, e.probeMinC, e.probeMaxC,
            e.flagConsistency, e.flagTempDiff, e.flagUnbalanced,
        )
        val cells = CellBlob.decodeCells(e.cells).toList()
        val probes = CellBlob.decodeProbes(e.probes).map { if (it == NO_TEMP) null else it }
        return (head + cells + probes).joinToString(";") { it?.toString() ?: "" }
    }

    fun fileName(nowWallMs: Long): String =
        "bydmate-cells-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(nowWallMs)) + ".csv"

    /** Streams the whole table into [dir] in pages of [PAGE]; the rows are never all in memory. */
    suspend fun write(dao: CellDao, dir: File, nowWallMs: Long): File {
        dir.mkdirs()
        val file = File(dir, fileName(nowWallMs))
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write(BOM + header(CarProfile.LEOPARD5_318.cells, CarProfile.LEOPARD5_318.probes) + "\n")
            var after = 0L
            while (true) {
                val rows = dao.page(after, PAGE)
                if (rows.isEmpty()) break
                rows.forEach { w.write(line(it) + "\n") }
                after = rows.last().id
            }
        }
        return file
    }

    private fun decimal(v: Double): String = BigDecimal(v.toString()).toPlainString().replace('.', ',')
}
