package com.bydmate.app.fork.cells.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CellCsvTest {
    private fun e(soc: Int?, currentA: Double?, probes: IntArray = IntArray(24) { 20 }) = CellSnapshotEntity(
        ts = 0L, elapsedMs = 0, profile = "leopard5_318", measure = "LOAD", restPeriod = null,
        relax = "NOT_NEEDED", relaxDropMv = null, soc = soc, currentA = currentA, packV = 580, charging = false,
        restMin = null, daysSinceFull = null, vminMv = 3300, vmaxMv = 3310, spreadMv = 10, probeMinC = 20, probeMaxC = 20,
        flagConsistency = null, flagTempDiff = null, flagUnbalanced = null,
        cells = CellBlob.encodeCells(IntArray(184) { 3300 }), probes = CellBlob.encodeProbes(probes),
    )

    @Test fun `csv uses a decimal comma and empty fields for null`() {
        val f = CellCsv.line(e(soc = null, currentA = 3.1)).split(';')
        assertEquals("LOAD", f[1])
        assertEquals("", f[2])
        assertEquals("3,1", f[3])
        assertEquals("580", f[4])
        assertTrue(CellCsv.line(e(50, 3.1)).contains(";50;3,1;"))
    }

    @Test fun `no temp probe is an empty field`() {
        val p = IntArray(24) { 20 }.also { it[1] = com.bydmate.app.fork.cells.stream.NO_TEMP }
        val f = CellCsv.line(e(50, 1.0, p)).split(';')
        assertEquals(CellCsv.header(184, 24).split(';').size, f.size)
        assertEquals("", f[f.size - 23])
    }

    @Test fun `a failed export leaves no partial file`() {
        val dir = java.io.File.createTempFile("csv", "").let { it.delete(); it.mkdirs(); it }
        val dao = io.mockk.mockk<CellDao>()
        io.mockk.coEvery { dao.page(any(), any()) } throws IllegalStateException("boom")
        try {
            kotlinx.coroutines.runBlocking { CellCsv.write(dao, dir, 0L) }
            org.junit.Assert.fail("must rethrow")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
        assertEquals(0, dir.listFiles()!!.size)
        dir.deleteRecursively()
    }

    @Test fun `header names`() {
        val h = CellCsv.header(184, 24).split(';')
        assertEquals("time", h[0])
        assertEquals("flag_unbalanced", h[15])
        assertEquals("c1", h[16])
        assertEquals("p24", h.last())
    }
}
