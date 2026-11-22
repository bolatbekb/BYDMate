package com.bydmate.app.fork.cells.stream

const val GB_DEVICE = 1039
val GB_STREAM_FID: Int = 0x9900001F.toInt()
const val MAX_CELL_PAGES = 100
const val MAX_PROBE_PAGES = 16
const val FRESH_MS = 10_000L
const val NO_VALUE = -1
const val NO_TEMP = Int.MIN_VALUE

private const val ENVELOPE_BYTES = 10

/** One GB stream buffer: CAN id (4 bytes BE), sub, channel, 4 counter bytes, then the CAN payload. */
data class GbFrame(val can: Int, val sub: Int, val ch: Int, val payload: ByteArray)

/** Null unless [fid] is the GB stream callback and the buffer holds a full envelope. */
fun parseGbEnvelope(fid: Int, b: ByteArray): GbFrame? {
    if (fid != GB_STREAM_FID || b.size < ENVELOPE_BYTES) return null
    val can = (u(b[0]) shl 24) or (u(b[1]) shl 16) or (u(b[2]) shl 8) or u(b[3])
    return GbFrame(can, u(b[4]), u(b[5]), b.copyOfRange(ENVELOPE_BYTES, b.size))
}

internal fun u(b: Byte): Int = b.toInt() and 0xFF
