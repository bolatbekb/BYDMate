package com.bydmate.app.fork.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefuelMathTest {
    @Test fun litersAndPriceGiveTotal() {
        assertEquals(Triple(53.97, 14302.05, 265.0), RefuelMath.complete(53.97, null, 265.0))
    }
    @Test fun totalAndPriceGiveLiters() {
        assertEquals(27.12, RefuelMath.complete(null, 8000.0, 295.0)!!.first, 0.0)
    }
    @Test fun litersAndTotalGivePrice() {
        assertEquals(50.0, RefuelMath.complete(10.0, 500.0, null)!!.third, 0.0)
    }
    @Test fun allThreeReturnedAsIs() {
        assertEquals(Triple(10.0, 999.0, 1.0), RefuelMath.complete(10.0, 999.0, 1.0))
    }
    @Test fun tooFewIsNull() { assertNull(RefuelMath.complete(10.0, null, null)) }
    @Test fun divisionByZeroIsNull() {
        assertNull(RefuelMath.complete(0.0, 100.0, null))
        assertNull(RefuelMath.complete(null, 100.0, 0.0))
    }
}
