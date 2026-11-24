package com.bydmate.app.fork.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dotted-int version comparison, matching the author's UpdateChecker.isNewer rule. */
class VersionsTest {

    @Test
    fun `numeric segments compare, not lexical`() {
        assertTrue(Versions.isNewer("3.10", "3.9"))
        assertFalse(Versions.isNewer("3.9", "3.10"))
    }

    @Test
    fun `missing trailing segments are zero`() {
        assertFalse(Versions.isNewer("3.17", "3.17.0"))
        assertFalse(Versions.isNewer("3.17.0", "3.17"))
        assertTrue(Versions.isNewer("3.17.1", "3.17"))
    }

    @Test
    fun `equal is not newer`() {
        assertFalse(Versions.isNewer("4.5.0.4", "4.5.0.4"))
    }

    @Test
    fun `v prefix and suffixes on the numeric head are tolerated`() {
        assertTrue(Versions.isNewer("v4.5.0.5", "4.5.0.4"))
        assertTrue(Versions.isNewer("4.5.0.5-test", "4.5.0.4"))
    }
}
