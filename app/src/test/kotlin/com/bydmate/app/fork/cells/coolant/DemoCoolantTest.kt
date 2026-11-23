package com.bydmate.app.fork.cells.coolant

import com.bydmate.app.data.remote.DiParsData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCoolantTest {
    private fun car(rpm: Int): DiParsData = mockk {
        every { engineRpm } returns rpm
        every { exteriorTemp } returns 7
    }

    @Test fun `demo coolant warms with the engine and cools without`() = runBlocking {
        var now = 0L
        val live = MutableStateFlow<DiParsData?>(car(1500))
        val demo = DemoCoolant(live) { now }
        var t = demo.readC()!!
        repeat(120) { now += 5_000; t = demo.readC()!!; assertTrue(t in -39..150) }
        assertTrue("warm $t", t > 75)
        live.value = car(0)
        repeat(720) { now += 5_000; t = demo.readC()!!; assertTrue(t in -39..150) }
        assertTrue("cold $t", t < 40)
    }
}
