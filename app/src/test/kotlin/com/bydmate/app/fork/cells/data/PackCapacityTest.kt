package com.bydmate.app.fork.cells.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackCapacityTest {
    @Test fun `the entered capacity is read with a comma or a point`() {
        assertEquals(31.8, capacityOf("31,8")!!, 1e-9)
        assertEquals(45.8, capacityOf(" 45.8 ")!!, 1e-9)
    }

    @Test fun `an empty or insane field gives none, so the car profile stands in`() {
        assertNull(capacityOf(null))
        assertNull(capacityOf(""))
        assertNull(capacityOf("abc"))
        assertNull(capacityOf("0"))
        assertNull(capacityOf("4.9"))
        assertNull(capacityOf("318"))
    }
}
