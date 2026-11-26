package com.bydmate.app.fork.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkDiagnosticsTest {

    @Test fun `band from frequency`() {
        assertEquals("2.4 GHz", bandOf(2412))
        assertEquals("5 GHz", bandOf(5180))
        assertEquals("6 GHz", bandOf(5955))
        assertEquals("—", bandOf(0))
    }

    @Test fun `mbps from bytes over millis`() {
        assertEquals(40.0, mbps(5_000_000, 1000), 0.001)
        assertEquals(0.0, mbps(5_000_000, 0), 0.001)
    }

    @Test fun `ping summary parses sent received loss and avg`() {
        val out = """
            PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.
            64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=20.1 ms
            --- 8.8.8.8 ping statistics ---
            5 packets transmitted, 5 received, 0% packet loss, time 4005ms
            rtt min/avg/max/mdev = 20.123/25.456/30.789/3.210 ms
        """.trimIndent()
        val s = parsePing(out)!!
        assertEquals(5, s.sent)
        assertEquals(5, s.received)
        assertEquals(0, s.lossPct)
        assertEquals(25.456, s.avgMs!!, 0.001)
    }

    @Test fun `ping loss parses and missing summary is null`() {
        val loss = parsePing("5 packets transmitted, 3 received, 40% packet loss")!!
        assertEquals(3, loss.received)
        assertEquals(40, loss.lossPct)
        assertNull(loss.avgMs)
        assertNull(parsePing("PING 8.8.8.8: no reply"))
    }
}
