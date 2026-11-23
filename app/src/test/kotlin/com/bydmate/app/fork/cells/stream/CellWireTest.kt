package com.bydmate.app.fork.cells.stream

import android.os.IBinder
import android.os.Parcel
import com.bydmate.app.helper.HelperBinderProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class CellWireTest {

    @Test fun `fork codes stay clear of every author code`() {
        val author = HelperBinderProtocol::class.java.declaredFields
            .filter { it.name.startsWith("TX_") && it.type == Int::class.javaPrimitiveType }
            .onEach { it.isAccessible = true }
            .map { it.getInt(HelperBinderProtocol) }
        assertTrue(author.isNotEmpty())
        assertTrue(author.none { it in CellTx.RANGE })
        assertTrue(CellTx.RANGE.last < IBinder.LAST_CALL_TRANSACTION)
        assertTrue(CellTx.LISTEN in CellTx.RANGE && CellTx.SNAPSHOT in CellTx.RANGE)
    }

    @Test fun `snapshot survives the round trip`() {
        val sent = feedFixture().snapshot(true, CellTx.REG_OK, 4_321L)
        val p = Parcel.obtain()
        writeCellSnapshot(p, sent)
        p.setDataPosition(0)
        val got = readCellSnapshot(p)!!
        p.recycle()

        assertArrayEquals(sent.cellMv, got.cellMv)
        assertArrayEquals(sent.cellPageMs, got.cellPageMs)
        assertArrayEquals(sent.probeC, got.probeC)
        assertArrayEquals(sent.probePageMs, got.probePageMs)
        // Scalars: the same arrays on both sides leave only the scalars to compare.
        assertEquals(sent.copy(), got.copy(cellMv = sent.cellMv, cellPageMs = sent.cellPageMs, probeC = sent.probeC, probePageMs = sent.probePageMs))
        assertEquals(184, got.declaredCells)
        assertTrue(got.listening)
        assertEquals(4_321L, got.listeningSinceMs)
        assertTrue(readingOf(got) is CellReading.Complete)
    }

    @Test fun `listen reply survives the round trip`() {
        val sent = CellListenReply(CellTx.REG_NOT_IN_MAP, "not in feature map")
        val p = Parcel.obtain()
        writeListenReply(p, sent)
        p.setDataPosition(0)
        assertEquals(sent, readListenReply(p))
        p.recycle()
    }

    @Test fun `truncated parcel reads as null`() {
        val full = Parcel.obtain()
        writeCellSnapshot(full, feedFixture().snapshot(true, CellTx.REG_OK, 0L))
        full.setDataSize(full.dataSize() - 4)
        full.setDataPosition(0)
        assertNull(readCellSnapshot(full))
        full.recycle()

        // Only the scalars, no arrays.
        val scalars = Parcel.obtain()
        scalars.writeLong(1L); scalars.writeInt(1); scalars.writeInt(1)
        scalars.setDataPosition(0)
        assertNull(readCellSnapshot(scalars))
        scalars.recycle()

        val reply = Parcel.obtain()
        reply.writeInt(CellTx.REG_OK)
        reply.setDataPosition(0)
        assertNull(readListenReply(reply))
        reply.recycle()

        assertNull(readListenReply(Parcel.obtain()))
    }

    @Test fun `arrays of another size read as null`() {
        val odd = feedFixture().snapshot(true, CellTx.REG_OK, 0L).let { it.copy(cellMv = it.cellMv.copyOf(MAX_CELL_PAGES * 3 - 1)) }
        val p = Parcel.obtain()
        writeCellSnapshot(p, odd)
        p.setDataPosition(0)
        assertNull(readCellSnapshot(p))
        p.recycle()
    }
}
