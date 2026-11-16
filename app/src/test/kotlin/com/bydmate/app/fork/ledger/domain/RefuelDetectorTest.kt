package com.bydmate.app.fork.ledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefuelDetectorTest {
    private fun step(s: DetectorState?, pct: Int?, odo: Double? = 100.0, ts: Long = 1L, total: Double? = 10.0) =
        RefuelDetector.step(s, pct, total, odo, ts, 10)

    private val base = DetectorState(30, 10.0, 100.0, 0L, null)

    @Test fun jumpAfterRestartCreatesDraft() {
        val (s, a) = step(base, 85)
        assertTrue(a is DetectorAction.CreateDraft)
        a as DetectorAction.CreateDraft
        assertEquals(30, a.before.baselinePercent)
        assertEquals(85, a.afterPercent)
        assertEquals(base, s)
    }

    @Test fun smallRiseIsIgnored() {
        assertEquals(DetectorAction.None, step(base, 36).second)
    }

    @Test fun gradualRiseMakesOneDraft() {
        var s: DetectorState? = null
        var creates = 0
        var updates = 0
        for (p in 30..90 step 2) {
            val (n, a) = step(s, p)
            s = n
            when (a) {
                is DetectorAction.CreateDraft -> { creates++; s = n!!.copy(draftId = 7L) }
                is DetectorAction.UpdateDraft -> updates++
                DetectorAction.None -> {}
            }
        }
        assertEquals(1, creates)
        assertTrue(updates > 0)
    }

    @Test fun drivingResetsBaseline() {
        val (s, a) = step(base, 85, odo = 105.0)
        assertEquals(DetectorAction.None, a)
        assertEquals(85, s!!.baselinePercent)
        assertEquals(105.0, s.baselineOdo, 0.0)
        assertNull(s.draftId)
    }

    @Test fun fuelDropLowersBaseline() {
        val (s, a) = step(base, 25, ts = 9L, total = 11.0)
        assertEquals(DetectorAction.None, a)
        assertEquals(DetectorState(25, 11.0, 100.0, 9L, null), s)
    }

    @Test fun fuelDropMovesBaselineOdometerToo() {
        val (s, _) = step(base, 25, odo = 101.5, ts = 9L, total = 11.0)
        assertEquals(DetectorState(25, 11.0, 101.5, 9L, null), s)
    }

    @Test fun zeroReadingIsIgnored() {
        var s: DetectorState? = base.copy(baselinePercent = 45)
        val actions = listOf(0, 45).map { p -> step(s, p).also { s = it.first }.second }
        assertEquals(listOf<DetectorAction>(DetectorAction.None, DetectorAction.None), actions)
        assertEquals(45, s!!.baselinePercent)
    }

    @Test fun slowDropStillLowersBaseline() {
        var s: DetectorState? = base.copy(baselinePercent = 45)
        for (p in 45 downTo 20) s = step(s, p).first
        assertEquals(20, s!!.baselinePercent)
        assertEquals(DetectorAction.None, step(s, 25).second)
    }

    @Test fun sentinel255Ignored() {
        val (s, a) = step(base, 255)
        assertEquals(base, s)
        assertEquals(DetectorAction.None, a)
    }

    @Test fun nullOdometerIgnored() {
        val (s, a) = step(base, 85, odo = null)
        assertEquals(base, s)
        assertEquals(DetectorAction.None, a)
    }

    @Test fun dismissedBaselineDoesNotRecreate() {
        assertEquals(DetectorAction.None, step(base.copy(dismissed = true), 85).second)
    }

    @Test fun jsonRoundTrip() {
        val s = DetectorState(30, null, 100.5, 5L, 3L, true)
        assertEquals(s, DetectorState.fromJson(s.toJson()))
        assertNull(DetectorState.fromJson("garbage"))
    }
}
