package com.bydmate.app.ui.home.graphite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphiteRulesTest {
    @Test fun scales() {
        assertEquals(listOf(0, 100, 200, 300, 400, 500, 550), rangeScale(550).ticks)
        assertEquals(listOf(0, 100, 200, 300, 400, 530), rangeScale(530).ticks)
        assertEquals(listOf(0, 20, 40, 64), rangeScale(64).ticks)
        assertEquals(listOf(0), rangeScale(0).ticks)
    }
    @Test fun longRangeKeepsScaleReadable() {
        assertEquals(listOf(0, 200, 400, 600, 800, 1000, 1200), rangeScale(1200).ticks)
        assertEquals(listOf(0, 200, 400, 600, 700), rangeScale(700).ticks)
        assertEquals(listOf(0, 500, 1000, 1500, 2100), rangeScale(2100).ticks)
    }
    @Test fun temps() {
        assertEquals(0.2875f, tempFraction(TempKind.OUTSIDE, -7), 0.0001f)
        assertEquals(1f, tempFraction(TempKind.CABIN, 99), 0f)
        assertEquals(0f, tempFraction(TempKind.BATTERY, -50), 0f)
    }
    @Test fun notes() {
        assertEquals("Гололёд", outsideNote(3))
        assertNull(outsideNote(4))
        assertNull(outsideNote(null))
        assertEquals("Авто · 22,0°", cabinNote(true, true, 22))
        assertEquals("Климат · 22,0°", cabinNote(false, true, 22))
        assertNull(cabinNote(null, false, 22))
        assertNull(cabinNote(true, false, 22))
    }
    @Test fun chips() {
        assertEquals("Включен · Ready", powerChip(2))
        assertEquals("Включен", powerChip(1))
        assertEquals("Выключен", powerChip(0))
        assertNull(powerChip(null))
        assertNull(engineChip(0))
        assertEquals("Двигатель заведён · 1 240 об/мин", engineChip(1240))
        assertEquals("Гибрид · Эко", modeChip("HEV", "ECO"))
        assertEquals("Электро · Норма", modeChip("EV", "NORMAL"))
        assertEquals("Гибрид · Snow", modeChip("HEV", "SNOW"))
        assertEquals("Электро", modeChip("EV", null))
        assertNull(modeChip(null, "ECO"))
    }
    @Test fun litres() {
        assertEquals(30, litresFromPercent(50, 60.0))
        assertNull(litresFromPercent(null, 60.0))
    }
}
