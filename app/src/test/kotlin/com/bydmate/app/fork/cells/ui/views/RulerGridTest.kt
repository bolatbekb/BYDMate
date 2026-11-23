package com.bydmate.app.fork.cells.ui.views

import org.junit.Assert.assertEquals
import org.junit.Test

class RulerGridTest {
    @Test fun `184 cells keep the 46 by 4 and 23 by 8 grids`() {
        assertEquals(46, gridColumns(184, narrow = false))
        assertEquals(23, gridColumns(184, narrow = true))
    }

    @Test fun `every pack fits, columns times rows hold all cells`() {
        for (n in listOf(1, 96, 120, 184, 200, 255, 300)) {
            for (narrow in listOf(false, true)) {
                val cols = gridColumns(n, narrow)
                val rows = (n + cols - 1) / cols
                assert(cols * rows >= n) { "$n cells in $cols × $rows" }
                assert(cols <= if (narrow) 25 else 50) { "$n cells: $cols columns" }
            }
        }
    }

    @Test fun `the strip marks follow the pack`() {
        assertEquals(listOf(1, 46, 92, 138, 184), stripMarks(184))
        assertEquals(listOf(1, 50, 100, 150, 200), stripMarks(200))
        assertEquals(listOf(1, 2, 3), stripMarks(3))
        assertEquals(emptyList<Int>(), stripMarks(0))
    }

    @Test fun `a bigger pack takes more rows, not smaller tiles`() {
        assertEquals(50, gridColumns(300, narrow = false))
        assertEquals(25, gridColumns(300, narrow = true))
        assertEquals(50, gridColumns(200, narrow = false))
    }
}
