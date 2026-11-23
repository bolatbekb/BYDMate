package com.bydmate.app.fork.cells.collect

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadAlignTest {

    @Test
    fun `pages taken at different currents line up`() {
        // Three pages read at three currents: 3300, 3250 and 3200 mV, no cell out of its page.
        val raw = intArrayOf(3300, 3300, 3300, 3250, 3250, 3250, 3200, 3200, 3200)
        val out = alignedUnderLoad(raw)
        assertEquals(0, out.max() - out.min())
        assertArrayEquals(IntArray(9) { 3250 }, out)
    }

    @Test
    fun `a cell sagging against its page-mates keeps its offset`() {
        val raw = intArrayOf(3300, 3300, 3300, 3250, 3220, 3250, 3200, 3200, 3200)
        val out = alignedUnderLoad(raw)
        assertEquals(3250 - 30, out[4])
        assertEquals(3250, out[3])
        assertEquals(3250, out[8])
    }

    @Test
    fun `the short last page is set against the page before it`() {
        // 184 cells = 61 full pages and one cell; here 2 full pages and one cell.
        val raw = intArrayOf(3300, 3302, 3301, 3200, 3201, 3202, 3195)
        val out = alignedUnderLoad(raw)
        // Page 1 median 3201, array median 3202: the lone cell is 6 below page 1.
        assertEquals(3202 - 6, out[6])
    }

    @Test
    fun `the car sweep from the drive log shrinks to the real spread`() {
        // Shape of the 21:10:06 sweep: one block of pages under regen (+108 mV), the rest under load.
        val raw = IntArray(184) { i -> (if (i / 3 in 20..29) 3330 else 3222) + (i % 3) }
        val out = alignedUnderLoad(raw)
        assertEquals(108 + 2, raw.max() - raw.min())
        assertEquals(2, out.max() - out.min())
    }

    @Test
    fun `fewer cells than a page come back as they are`() {
        assertArrayEquals(intArrayOf(3300, 3290), alignedUnderLoad(intArrayOf(3300, 3290)))
    }
}
