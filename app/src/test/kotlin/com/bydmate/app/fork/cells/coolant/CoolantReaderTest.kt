package com.bydmate.app.fork.cells.coolant

import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.data.vehicle.HelperClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoolantReaderTest {
    @Test fun `sentinels and out of range read as a dash`() {
        assertNull(coolantOrNull(65535))
        assertNull(coolantOrNull(-40))
        assertEquals(-39, coolantOrNull(-39))
        assertEquals(150, coolantOrNull(150))
        assertNull(coolantOrNull(151))
    }

    private fun reader(reply: List<Pair<Int, Int>>?): CoolantReader {
        val helper = mockk<HelperClient>()
        coEvery { helper.readBatch(listOf(BatchReadItem(5, 1006, 843055176))) } returns reply
        return HelperCoolantReader(helper)
    }

    @Test fun `helper reader takes only status 0`() = runBlocking {
        assertNull(reader(listOf(-1 to 88)).readC())
        assertEquals(88, reader(listOf(0 to 88)).readC())
        assertNull(reader(null).readC())
    }
}
