package com.bydmate.app.fork.cells.data

import com.bydmate.app.fork.cells.stream.NO_TEMP
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CellBlobTest {
    @Test fun `cells blob round trips 184 values`() {
        val mv = IntArray(184) { 2000 + it * 10 }
        mv[0] = 4000; mv[1] = 0; mv[2] = 65535
        val b = CellBlob.encodeCells(mv)
        assertEquals(368, b.size)
        assertArrayEquals(mv, CellBlob.decodeCells(b))
    }

    @Test fun `probes blob keeps NO_TEMP`() {
        val c = intArrayOf(25, -40, 125, NO_TEMP, 0, 7)
        val b = CellBlob.encodeProbes(c)
        assertEquals(6, b.size)
        assertEquals(0x7F.toByte(), b[3])
        assertArrayEquals(c, CellBlob.decodeProbes(b))
    }
}
