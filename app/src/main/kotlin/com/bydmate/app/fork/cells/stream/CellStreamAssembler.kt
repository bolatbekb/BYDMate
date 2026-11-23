package com.bydmate.app.fork.cells.stream

private const val CAN_COUNTS = 0x43A
private const val CAN_EXTREMES = 0x43B
private const val CAN_PROBES = 0x43C
private const val CAN_CELLS = 0x43D
private const val TEMP_OFFSET = 40
private const val EMPTY_U16 = 0xFFFF
private const val EMPTY_U8 = 0xFF
private const val CHECKSUM_OK = 255

/** Collects the BMS pages 0x43A..0x43D of the GB stream into cell and probe arrays. Thread-safe. */
class CellStreamAssembler(private val clock: () -> Long) {
    private val cellMv = IntArray(MAX_CELL_PAGES * 3) { NO_VALUE }
    private val cellPageMs = LongArray(MAX_CELL_PAGES)
    private val probeC = IntArray(MAX_PROBE_PAGES * 6) { NO_TEMP }
    private val probePageMs = LongArray(MAX_PROBE_PAGES)
    private var lastFrameMs = 0L
    private var frames = 0L
    private var rejected = 0L
    private var declaredCells = -1
    private var declaredProbes = -1
    private var hottest = -1
    private var hottestC = NO_TEMP
    private var coldest = -1
    private var coldestC = NO_TEMP

    /** Vendor thread; never throws. */
    fun onBuffer(fid: Int, bytes: ByteArray?) {
        runCatching {
            val frame = bytes?.let { parseGbEnvelope(fid, it) } ?: return
            if (frame.sub != 0 || frame.ch != 2) return
            synchronized(this) { accept(frame.can, frame.payload) }
        }
    }

    private fun accept(can: Int, p: ByteArray) {
        val now = clock()
        val ok = when (can) {
            CAN_CELLS -> p.size >= 7 && cellPage(p, now)
            CAN_PROBES -> p.size >= 8 && probePage(p, now)
            CAN_COUNTS -> (p.size >= 2).also { if (it) { declaredCells = u(p[0]); declaredProbes = u(p[1]) } }
            CAN_EXTREMES -> (p.size >= 4).also { if (it) extremes(p) }
            else -> false
        }
        if (ok) {
            lastFrameMs = now
            frames++
        }
    }

    private fun cellPage(p: ByteArray, now: Long): Boolean {
        val page = u(p[0])
        if (page >= MAX_CELL_PAGES) return true
        for (i in 0 until 3) {
            val v = u(p[1 + i * 2]) or (u(p[2 + i * 2]) shl 8)
            cellMv[page * 3 + i] = if (v == EMPTY_U16) NO_VALUE else v
        }
        cellPageMs[page] = now
        return true
    }

    private fun probePage(p: ByteArray, now: Long): Boolean {
        if (p.sumOf { u(it) } % 256 != CHECKSUM_OK) {
            rejected++
            return false
        }
        val page = u(p[0])
        if (page >= MAX_PROBE_PAGES) return true
        for (i in 0 until 6) {
            val v = u(p[1 + i])
            probeC[page * 6 + i] = if (v == EMPTY_U8) NO_TEMP else v - TEMP_OFFSET
        }
        probePageMs[page] = now
        return true
    }

    private fun extremes(p: ByteArray) {
        hottest = u(p[0])
        hottestC = u(p[1]) - TEMP_OFFSET
        coldest = u(p[2])
        coldestC = u(p[3]) - TEMP_OFFSET
    }

    @Synchronized
    fun snapshot(listening: Boolean, registration: Int, listeningSinceMs: Long): CellStreamSnapshot =
        CellStreamSnapshot(
            nowMs = clock(), listening = listening, registration = registration,
            listeningSinceMs = listeningSinceMs, lastFrameMs = lastFrameMs, frames = frames, rejected = rejected,
            declaredCells = declaredCells, declaredProbes = declaredProbes,
            cellMv = cellMv.copyOf(), cellPageMs = cellPageMs.copyOf(),
            probeC = probeC.copyOf(), probePageMs = probePageMs.copyOf(),
            hottest = hottest, hottestC = hottestC, coldest = coldest, coldestC = coldestC,
        )

    @Synchronized
    fun reset() {
        cellMv.fill(NO_VALUE); cellPageMs.fill(0L)
        probeC.fill(NO_TEMP); probePageMs.fill(0L)
        lastFrameMs = 0L; frames = 0L; rejected = 0L
        declaredCells = -1; declaredProbes = -1
        hottest = -1; hottestC = NO_TEMP; coldest = -1; coldestC = NO_TEMP
    }
}
