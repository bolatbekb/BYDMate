package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphiteLinksTest {
    @Test fun cellLabelNamesKnownNetworkTypes() {
        assertEquals("LTE", GraphiteLinks.cellLabel(13))
        assertEquals("5G", GraphiteLinks.cellLabel(20))
        listOf(3, 8, 9, 10, 15).forEach { assertEquals("3G", GraphiteLinks.cellLabel(it)) }
    }

    @Test fun cellLabelUnknownIsNull() {
        assertNull(GraphiteLinks.cellLabel(0))
        assertNull(GraphiteLinks.cellLabel(1))
    }
}
