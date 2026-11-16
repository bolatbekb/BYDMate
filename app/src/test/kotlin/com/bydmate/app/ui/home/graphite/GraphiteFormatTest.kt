package com.bydmate.app.ui.home.graphite

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphiteFormatTest {
    @Test fun ints() {
        assertEquals("1 240", grInt(1240))
        assertEquals("−7", grInt(-7))
        assertEquals("0", grInt(0))
    }
    @Test fun decimals() {
        assertEquals("47,3", grDec(47.3))
        assertEquals("1 284,0", grDec(1284.0))
    }
    @Test fun deg() {
        assertEquals("−7°", grDeg(-7))
        assertEquals("—", grDeg(null))
    }
    @Test fun durations() {
        assertEquals("1 ч 12 мин", grDuration(72))
        assertEquals("2 ч", grDuration(120))
        assertEquals("45 мин", grDuration(45))
    }
    @Test fun clockAndDate() {
        assertEquals("18:42", grClock(LocalDateTime.of(2026, 11, 16, 18, 42)))
        assertEquals("09:05", grClock(LocalDateTime.of(2026, 11, 16, 9, 5)))
        assertEquals("Пн, 16 ноября", grDate(LocalDate.of(2026, 11, 16)))
        assertEquals("Вс, 1 марта", grDate(LocalDate.of(2026, 3, 1)))
    }
    @Test fun trackTime() {
        assertEquals("1:47", grTrackTime(107_000))
        assertEquals("1:02:05", grTrackTime(3_725_000))
        assertEquals("—:—", grTrackTime(null))
    }
}
