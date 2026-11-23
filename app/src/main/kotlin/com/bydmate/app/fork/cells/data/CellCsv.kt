package com.bydmate.app.fork.cells.data

import com.bydmate.app.fork.cells.stream.NO_TEMP
import java.io.File
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV like the owner's cells CSV: `;` separator, decimal comma, empty field for null, UTF-8 with BOM (R25). Each row says
 * its own pack (`cell_count`, `probe_count`); the columns `c…` and `p…` run to the largest pack of the file, a smaller
 * pack's row is padded with empty fields, so a probe column always holds a probe.
 */
object CellCsv {
    const val PAGE = 500
    const val BOM = "\uFEFF"
    private val FIXED = listOf(
        "time", "measure", "soc", "current_a", "pack_v", "charging", "rest_min", "days_since_full", "relax",
        "relax_drop_mv", "spread_mv", "probe_min_c", "probe_max_c", "flag_consistency", "flag_temp_diff", "flag_unbalanced",
        "cell_count", "probe_count",
    )

    fun header(cells: Int, probes: Int): String =
        (FIXED + (1..cells).map { "c$it" } + (1..probes).map { "p$it" }).joinToString(";")

    /** One row with [cells] cell columns and [probes] probe columns (the file's largest pack). */
    fun line(e: CellSnapshotEntity, cells: Int, probes: Int, fmt: SimpleDateFormat = timeFormat()): String {
        val time = fmt.format(Date(e.ts))
        val cellsMv = CellBlob.decodeCells(e.cells)
        val probesC = CellBlob.decodeProbes(e.probes)
        val head = listOf(
            time, e.measure, e.soc, e.currentA?.let(::decimal), e.packV, if (e.charging) 1 else 0, e.restMin,
            e.daysSinceFull, e.relax, e.relaxDropMv, e.spreadMv, e.probeMinC, e.probeMaxC,
            e.flagConsistency, e.flagTempDiff, e.flagUnbalanced, cellsMv.size, probesC.size,
        )
        val cellCols = List(maxOf(cells, cellsMv.size)) { cellsMv.getOrNull(it) }
        val probeCols = List(maxOf(probes, probesC.size)) { i -> probesC.getOrNull(i)?.takeIf { it != NO_TEMP } }
        return (head + cellCols + probeCols).joinToString(";") { it?.toString() ?: "" }
    }

    private fun timeFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun fileName(nowWallMs: Long): String =
        "bydmate-cells-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(nowWallMs)) + ".csv"

    /** Streams the whole table into [dir] in pages of [PAGE]; the rows are never all in memory. */
    suspend fun write(dao: CellDao, dir: File, nowWallMs: Long): File {
        dir.mkdirs()
        val file = File(dir, fileName(nowWallMs))
        runCatching { writeAll(dao, file) }.onFailure { file.delete() }.getOrThrow()
        return file
    }

    private suspend fun writeAll(dao: CellDao, file: File) {
        val fmt = timeFormat()
        val pack = dao.packCounts()
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write(BOM + header(pack.cells, pack.probes) + "\n")
            var after = 0L
            while (true) {
                val rows = dao.page(after, PAGE)
                if (rows.isEmpty()) break
                rows.forEach { w.write(line(it, pack.cells, pack.probes, fmt) + "\n") }
                after = rows.last().id
            }
        }
    }

    private fun decimal(v: Double): String = BigDecimal(v.toString()).toPlainString().replace('.', ',')
}
