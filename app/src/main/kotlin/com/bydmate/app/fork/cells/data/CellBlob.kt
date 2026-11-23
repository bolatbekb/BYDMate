package com.bydmate.app.fork.cells.data

import com.bydmate.app.fork.cells.stream.NO_TEMP

/** Compact storage of one snapshot: cells as u16 little-endian mV, probes as signed bytes (0x7F = no probe). */
object CellBlob {
    private const val NO_TEMP_BYTE: Byte = 0x7F
    private const val BYTE_MASK = 0xFF
    private const val BYTE_BITS = 8

    fun encodeCells(mv: IntArray): ByteArray {
        val out = ByteArray(mv.size * 2)
        mv.forEachIndexed { i, v ->
            out[i * 2] = (v and BYTE_MASK).toByte()
            out[i * 2 + 1] = ((v shr BYTE_BITS) and BYTE_MASK).toByte()
        }
        return out
    }

    fun decodeCells(b: ByteArray): IntArray =
        IntArray(b.size / 2) { (b[it * 2].toInt() and BYTE_MASK) or ((b[it * 2 + 1].toInt() and BYTE_MASK) shl BYTE_BITS) }

    fun encodeProbes(c: IntArray): ByteArray =
        ByteArray(c.size) { if (c[it] == NO_TEMP) NO_TEMP_BYTE else c[it].toByte() }

    fun decodeProbes(b: ByteArray): IntArray = IntArray(b.size) { if (b[it] == NO_TEMP_BYTE) NO_TEMP else b[it].toInt() }
}
