package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.autoservice.Pm25
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals("Двигатель заведён", engineChip(1240))
        assertEquals("HEV · ECO", modeChip("HEV", "ECO"))
        assertEquals("EV · NORMAL", modeChip("EV", "NORMAL"))
        assertEquals("EV · SPORT", modeChip("EV", "SPORT"))
        assertEquals("HEV · SNOW", modeChip("HEV", "SNOW"))
        assertEquals("EV", modeChip("EV", null))
        assertEquals("HEV", modeChip("HEV", null))
        assertNull(modeChip(null, "ECO"))
    }
    @Test fun litres() {
        assertEquals(30, litresFromPercent(50, 60.0))
        assertNull(litresFromPercent(null, 60.0))
    }
    @Test fun pm25QualityByLevel() {
        assertEquals("отлично" to Tone.ELECTRIC, pm25Quality(1))
        assertEquals("хорошо" to Tone.ELECTRIC, pm25Quality(2))
        assertEquals("средне" to Tone.FUEL, pm25Quality(3))
        listOf(4, 5, 6).forEach { assertEquals("плохо" to Tone.HEAT, pm25Quality(it)) }
        assertNull(pm25Quality(null))
        assertNull(pm25Quality(0))
        assertNull(pm25Quality(7))
    }
    @Test fun pm25SidesNeedLevelAndValue() {
        assertNull(pm25Sides(null))
        assertNull(pm25Sides(Pm25(inside = null, outside = null, levelIn = 1, levelOut = 2)))
        assertNull(pm25Sides(Pm25(inside = 12, outside = 35, levelIn = 0, levelOut = null)))
        assertEquals(
            listOf(Pm25Side("Салон", 12, "отлично", Tone.ELECTRIC), Pm25Side("Снаружи", 35, "хорошо", Tone.ELECTRIC)),
            pm25Sides(Pm25(inside = 12, outside = 35, levelIn = 1, levelOut = 2)),
        )
        assertEquals(
            listOf(Pm25Side("Снаружи", 80, "плохо", Tone.HEAT)),
            pm25Sides(Pm25(inside = 12, outside = 80, levelIn = null, levelOut = 4)),
        )
    }
    @Test fun footerValues() {
        assertEquals("97 %", sohText(96.6f))
        assertEquals("13,8", voltageText(13.84))
        assertEquals("3\u202F500 кОм", insulationText(3_500))
        assertEquals("9\u202F999 кОм", insulationText(9_999))
        assertEquals("10,0 МОм", insulationText(10_000))
        assertEquals("12,5 МОм", insulationText(12_480))
    }
    @Test fun onlineNeedsLinkAndFreshData() {
        val now = 1_000_000L
        assertTrue(isOnline(connected = true, lastDataAtMs = now - 5_000, nowMs = now))
        assertFalse(isOnline(connected = false, lastDataAtMs = now - 5_000, nowMs = now))
        assertFalse(isOnline(connected = true, lastDataAtMs = 0L, nowMs = now))
        assertFalse(isOnline(connected = true, lastDataAtMs = now - 120_000, nowMs = now))
    }
}
