package com.bydmate.app.fork.carhost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiGuardTest {
    private val pkg = "a.b"

    private fun snap(
        top: String? = pkg, act: String? = "a.b.Main", nodes: Int = 57,
        ids: Set<String> = (1..10).map { "id$it" }.toSet(), texts: List<String> = listOf("Route"),
    ) = UiSnapshot(top, act, "$top/$act", ids, texts, nodes)

    @Test
    fun parseUiDump_readsIdsTextsAndCount() {
        val xml = """<?xml version='1.0'?><hierarchy rotation="0">
            <node index="0" text="" resource-id="a:id/root" content-desc="">
            <node index="0" text="Go" resource-id="a:id/btn" content-desc="" />
            <node index="1" text="" resource-id="" content-desc="Menu button" />
            <node index="2" text="" resource-id="a:id/btn" content-desc="" />
            </node></hierarchy>"""
        val (ids, texts, n) = UiGuard.parseUiDump(xml)
        assertEquals(setOf("a:id/root", "a:id/btn"), ids)
        assertEquals(listOf("Go", "Menu button"), texts)
        assertEquals(4, n)
    }

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
    fun verdict_same_isOk() = assertEquals(GuardVerdict.OK, UiGuard.verdict(snap(), snap(), pkg).first)

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
    fun verdict_carWordsAlreadyBefore_isNotBlocked() {
        val s = snap(texts = listOf("Android Auto"))
        assertEquals(GuardVerdict.OK, UiGuard.verdict(s, s, pkg).first)
    }

    @Test
    fun verdict_treeCollapsed() =
        assertEquals(GuardVerdict.BLOCKED to "tree_collapsed", UiGuard.verdict(snap(), snap(nodes = 10), pkg))

    @Test
    fun verdict_halfIdsReplaced_isChanged() {
        val ids = (1..5).map { "id$it" }.toSet() + (1..5).map { "new$it" }
        assertEquals(GuardVerdict.CHANGED to "tree_changed", UiGuard.verdict(snap(), snap(ids = ids), pkg))
    }

    @Test
    fun verdict_notInFrontBefore_isUnknown() =
        assertEquals(GuardVerdict.UNKNOWN to "not_in_front_before", UiGuard.verdict(snap(top = "x.y"), snap(), pkg))

    @Test
    fun verdict_bydmateOnTopAfter_isLeftFront() =
        assertEquals(GuardVerdict.BLOCKED to "left_front", UiGuard.verdict(snap(), snap(top = "com.bydmate.app"), pkg))
}
