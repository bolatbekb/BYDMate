package com.bydmate.app.fork.mycar

import com.bydmate.app.fork.alerts.fakePrefs
import com.bydmate.app.fork.cells.data.CarProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyCarLogicTest {

    // --- MyCarSettings ---

    @Test fun `car name round-trips and the autofill flag starts false`() {
        val s = MyCarSettings(fakePrefs())
        assertEquals("", s.carName.value)
        assertFalse(s.modelAutofilled)
        s.setCarName("Барс")
        assertEquals("Барс", s.carName.value)
        s.markModelAutofilled()
        assertTrue(s.modelAutofilled)
    }

    // --- profileForAutoType ---

    @Test fun `auto type 153 is Leopard 5, other codes are none`() {
        assertEquals(CarProfile.LEOPARD5_318, profileForAutoType(153))
        assertEquals(CarProfile.NONE, profileForAutoType(260))
        assertEquals(CarProfile.NONE, profileForAutoType(null))
    }

    // --- autofillModelOnce ---

    @Test fun `autofill sets the model and marks the flag when none is chosen`() {
        var set: CarProfile? = null
        var marked = false
        autofillModelOnce(false, CarProfile.NONE, 153, { set = it }, { marked = true })
        assertEquals(CarProfile.LEOPARD5_318, set)
        assertTrue(marked)
    }

    @Test fun `autofill does nothing once already autofilled or a model is chosen`() {
        var set: CarProfile? = null
        autofillModelOnce(true, CarProfile.NONE, 153, { set = it }, {})
        autofillModelOnce(false, CarProfile.LEOPARD5_318, 153, { set = it }, {})
        assertNull(set)
    }

    @Test fun `autofill retries later when the code is unknown - no set, no mark`() {
        var set: CarProfile? = null
        var marked = false
        autofillModelOnce(false, CarProfile.NONE, null, { set = it }, { marked = true })
        assertNull(set)
        assertFalse(marked)
    }
}
