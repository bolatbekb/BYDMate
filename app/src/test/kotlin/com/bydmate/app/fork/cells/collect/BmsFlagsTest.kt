package com.bydmate.app.fork.cells.collect

import com.bydmate.app.data.vehicle.BatchReadItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BmsFlagsTest {
    @Test fun `bmsFlagsOf maps statuses`() {
        assertEquals(BmsFlags(1, null, 0), bmsFlagsOf(listOf(0 to 1, -1 to 0, 0 to 0)))
        assertNull(bmsFlagsOf(null))
        // readBatch is never partial; a short answer is no answer.
        assertNull(bmsFlagsOf(listOf(0 to 1, 0 to 0)))
    }

    @Test fun `the three flags are getInt reads of the plan's ids`() {
        assertEquals(
            listOf(BatchReadItem(5, 1039, 1132462090), BatchReadItem(5, 1039, 1132462088), BatchReadItem(5, 1005, 882903102)),
            BMS_FLAG_ITEMS,
        )
    }
}
