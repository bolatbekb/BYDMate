package com.bydmate.app.fork.carhost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefusalReasonTest {
    private fun reasonsOf(vararg events: HostEvent): List<String?> {
        val flow = HostFlow(hostLatest = 5)
        return events.flatMap { flow.on(it) }
            .filterIsInstance<HostAction.Log>()
            .map { refusalReason(it.what, it.fields) }
    }

    @Test fun `api mismatch gives its reason`() {
        val r = reasonsOf(HostEvent.Bound, HostEvent.AppInfoGot(min = 9, latest = 9))
        assertEquals(listOf<String?>("api_mismatch"), r)
    }

    @Test fun `a failed call gives the error cut to 80 chars`() {
        val r = reasonsOf(HostEvent.Bound, HostEvent.CallFailed("handshake", "E".repeat(200)))
        assertEquals(listOf<String?>("E".repeat(FAIL_REASON_MAX)), r)
    }

    @Test fun `other lines give nothing`() {
        assertNull(refusalReason("carhost_state", listOf("state" to "FAILED")))
        assertNull(refusalReason("carhost_refused", emptyList()))
    }
}
