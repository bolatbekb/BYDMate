package com.bydmate.app.fork.carhost

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiGuardTest {
    private val pkg = "a.b"

    private fun snap(top: String? = pkg, act: String? = "a.b.Main", texts: List<String> = listOf("Route")) =
        UiSnapshot(top, act, "$top/$act", texts)

    @Test
    fun parseTop_readsActivityAndFocus() {
        val act = "  topResumedActivity=ActivityRecord{1f2 u0 a.b/.Main t12}\n  mResumedActivity: ActivityRecord{1f2 u0 a.b/.Main t12}"
        val win = "  mCurrentFocus=Window{9a u0 a.b/a.b.Main}"
        val (p, a, f) = UiGuard.parseTop(act, win)
        assertEquals("a.b", p)
        assertEquals("a.b.Main", a)
        assertEquals("a.b/a.b.Main", f)
        val e = UiGuard.parseTop("", "")
        assertNull(e.first); assertNull(e.second); assertNull(e.third)
    }

    @Test
    fun verdict_same_isOk() = assertEquals(GuardVerdict.OK to "same", UiGuard.verdict(snap(), snap(), pkg))

    @Test
    fun verdict_activityChanged() =
        assertEquals(GuardVerdict.BLOCKED to "activity_changed", UiGuard.verdict(snap(), snap(act = "a.b.Stub"), pkg))

    @Test
    fun verdict_carWords() =
        assertEquals(
            GuardVerdict.BLOCKED to "car_words",
            UiGuard.verdict(snap(), snap(texts = listOf("Route", "Навигатор работает в автомобиле")), pkg),
        )

    @Test
    fun verdict_androidAutoNotificationAfterConnect_isBlocked() =
        assertEquals(
            GuardVerdict.BLOCKED to "car_words",
            UiGuard.verdict(snap(texts = emptyList()), snap(texts = listOf("Navi | Running in Android Auto mode")), pkg),
        )

    @Test
    fun verdict_carWordsAlreadyBefore_isNotBlocked() {
        val s = snap(texts = listOf("Android Auto"))
        assertEquals(GuardVerdict.OK, UiGuard.verdict(s, s, pkg).first)
    }

    @Test
    fun verdict_noNotifications_isOk() =
        assertEquals(GuardVerdict.OK, UiGuard.verdict(snap(texts = emptyList()), snap(texts = emptyList()), pkg).first)

    @Test
    fun verdict_notInFrontBefore_isUnknown() =
        assertEquals(GuardVerdict.UNKNOWN to "not_in_front_before", UiGuard.verdict(snap(top = "x.y"), snap(), pkg))

    @Test
    fun verdict_emptyBefore_isNoReading() =
        assertEquals(GuardVerdict.UNKNOWN to "no_reading", UiGuard.verdict(snap(top = null, act = null), snap(), pkg))

    @Test
    fun verdict_bydmateOnTopAfter_isLeftFront() =
        assertEquals(GuardVerdict.BLOCKED to "left_front", UiGuard.verdict(snap(), snap(top = "com.bydmate.app"), pkg))

    @Test
    fun verdict_failedTopRead_isNoReading() =
        assertEquals(GuardVerdict.UNKNOWN to "no_reading", UiGuard.verdict(snap(), snap(top = null, act = null), pkg))

    @Test
    fun readBefore_emptyFirstRead_retriesOnceAfterWait() = runTest {
        val tags = mutableListOf<String>()
        var waits = 0
        val reads = ArrayDeque(listOf(snap(top = null, act = null), snap()))
        val s = readBefore({ tags += it; reads.removeFirst() }, { waits++ })
        assertEquals(listOf("before", "before-retry"), tags)
        assertEquals(1, waits)
        assertEquals(pkg, s.pkgOnTop)
    }

    @Test
    fun readBefore_emptyTwice_givesNoReading() = runTest {
        val tags = mutableListOf<String>()
        val s = readBefore({ tags += it; snap(top = null, act = null) }, {})
        assertEquals(2, tags.size)
        assertEquals(GuardVerdict.UNKNOWN to "no_reading", UiGuard.verdict(s, snap(), pkg))
    }

    @Test
    fun readBefore_goodFirstRead_noRetry() = runTest {
        val tags = mutableListOf<String>()
        var waits = 0
        readBefore({ tags += it; snap() }, { waits++ })
        assertEquals(listOf("before"), tags)
        assertEquals(0, waits)
    }
}
