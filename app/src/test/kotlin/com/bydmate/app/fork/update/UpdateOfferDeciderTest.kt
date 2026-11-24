package com.bydmate.app.fork.update

import org.junit.Assert.assertEquals
import org.junit.Test

/** The app-open decision: offer (parked), notify (moving), or silent (nothing ready). */
class UpdateOfferDeciderTest {

    @Test
    fun `no ready apk is always silent, parked or not`() {
        assertEquals(UpdateOffer.SILENT, UpdateOfferDecider.decide(hasReadyApk = false, parked = true))
        assertEquals(UpdateOffer.SILENT, UpdateOfferDecider.decide(hasReadyApk = false, parked = false))
    }

    @Test
    fun `ready apk and parked offers the install`() {
        assertEquals(UpdateOffer.OFFER, UpdateOfferDecider.decide(hasReadyApk = true, parked = true))
    }

    @Test
    fun `ready apk but not parked only notifies`() {
        assertEquals(UpdateOffer.NOTIFY, UpdateOfferDecider.decide(hasReadyApk = true, parked = false))
    }
}
