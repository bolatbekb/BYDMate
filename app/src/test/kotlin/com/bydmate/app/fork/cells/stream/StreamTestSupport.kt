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
