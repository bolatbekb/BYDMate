package com.bydmate.app.fork.cells.stream

internal fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

internal fun msOfDay(t: String): Long {
    val (h, m, rest) = t.split(":")
    val (s, ms) = rest.split(".")
    return ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms.toLong()
}

internal class FakeClock(var now: Long = 1_000L) : () -> Long {
    override fun invoke(): Long = now
}

internal fun feedLine(a: CellStreamAssembler, clock: FakeClock, line: String) {
    val parts = line.trim().split(" ")
    clock.now = msOfDay(parts[0])
    val fid = parts[2].removePrefix("fid=").toLong(16).toInt()
    a.onBuffer(fid, hex(parts[4]))
}

internal fun fixtureLines(): List<String> =
    CellStreamAssembler::class.java.getResourceAsStream("/fork/cells/gb-pass-20261122.txt")!!
        .bufferedReader().readLines().filter { it.isNotBlank() }

internal fun feedFixture(clock: FakeClock = FakeClock(), a: CellStreamAssembler = CellStreamAssembler(clock)): CellStreamAssembler {
    fixtureLines().forEach { feedLine(a, clock, it) }
    return a
}

private fun envelope(can: Int, payload: ByteArray): ByteArray =
    byteArrayOf((can ushr 24).toByte(), (can ushr 16).toByte(), (can ushr 8).toByte(), can.toByte(), 0, 2, 0, 0, 0, 1) + payload

/** A synthetic pack of [cells] cells (3300 + i % 5 mV) and [probes] probes (20 + i % 3 °C), every page once, counts first. */
internal fun feedPack(a: CellStreamAssembler, cells: Int, probes: Int) {
    a.onBuffer(GB_STREAM_FID, envelope(0x43A, byteArrayOf(cells.toByte(), probes.toByte(), 0, 0, 0, 0, 0, 0)))
    for (page in 0 until (cells + 2) / 3) {
        val p = ByteArray(8)
        p[0] = page.toByte()
        for (i in 0 until 3) {
            val cell = page * 3 + i
            val v = if (cell < cells) 3300 + cell % 5 else 0xFFFF
            p[1 + i * 2] = v.toByte(); p[2 + i * 2] = (v ushr 8).toByte()
        }
        a.onBuffer(GB_STREAM_FID, envelope(0x43D, p))
    }
    for (page in 0 until (probes + 5) / 6) {
        val p = ByteArray(8)
        p[0] = page.toByte()
        for (i in 0 until 6) {
            val probe = page * 6 + i
            p[1 + i] = (if (probe < probes) 60 + probe % 3 else 0xFF).toByte()
        }
        val sum = p.take(7).sumOf { it.toInt() and 0xFF }
        p[7] = ((255 - sum % 256 + 256) % 256).toByte()
        a.onBuffer(GB_STREAM_FID, envelope(0x43C, p))
    }
}
