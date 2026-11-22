package com.bydmate.app.fork.cells.verdict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaggardsTest {
    private fun rest(n: Int, lowest: (Int) -> Int = { 91 }, spread: Int = 5) =
        (0 until n).map { m(spread, id = it + 1L, ts = NOON - it * DAY, lowest = lowest(it)) }

    @Test fun `lowest in 3 of the last 6 rest periods is a laggard`() {
        val list = rest(6, lowest = { if (it < 3) 91 else 10 + it })
        assertEquals(listOf(Laggard(92, 3, 6)), Laggards.find(list))
    }

    @Test fun `several measurements of one rest period count once`() {
        val list = (0 until 3).map { m(5, id = it + 1L, ts = NOON - it * 1000L, period = 7) }
        assertTrue(Laggards.find(list).isEmpty())
    }

    @Test fun `noticeably low counts as lagging`() {
        // cell 92 is 11 mV under the median (noticeably low), another cell is the strict lowest
        val list = (0 until 3).map {
            val x = m(30, id = it + 1L, ts = NOON - it * DAY, lowest = 40)
            x.cellsMv[91] = 3275 - 11
            x
        }
        val found = Laggards.find(list).map { it.cell }
        assertTrue(92 in found)
        assertTrue(41 in found)
    }

    @Test fun `ties count for every tied cell`() {
        val list = (0 until 3).map {
            val x = m(5, id = it + 1L, ts = NOON - it * DAY, lowest = 10)
            x.cellsMv[20] = 3270
            x
        }
        assertEquals(setOf(11, 21), Laggards.find(list).map { it.cell }.toSet())
    }

    @Test fun `fewer than 3 measurements give none`() {
        assertTrue(Laggards.find(rest(2)).isEmpty())
    }
}
