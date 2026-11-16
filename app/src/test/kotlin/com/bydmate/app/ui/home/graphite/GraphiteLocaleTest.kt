package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.graphics.Color
import com.bydmate.app.R
import com.bydmate.app.data.autoservice.Pm25
import com.bydmate.app.ui.home.cdrive.ChartPeriod
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure functions of the Graphite home in each app language; RU is what the home shows today. */
class GraphiteLocaleTest {
    private val nnbsp = ' '
    private val nbsp = ' '

    // 2026-11-16 is a Monday; the week window runs Tuesday..Monday.
    private val monday = ZonedDateTime.of(2026, 11, 16, 18, 42, 0, 0, ZoneId.of("Europe/Moscow"))

    @Test fun languageTags() {
        assertEquals(GrLang.RU, grLang("ru"))
        assertEquals(GrLang.EN, grLang("en"))
        assertEquals(GrLang.EN, grLang("en-US"))
        assertEquals(GrLang.ZH, grLang("zh"))
        assertEquals(GrLang.ZH, grLang("zh_CN"))
        assertEquals(GrLang.PT, grLang("pt-BR"))
        assertEquals(GrLang.PL, grLang("PL"))
        assertEquals(GrLang.BE, grLang("be"))
        assertEquals(GrLang.RU, grLang(null))
        assertEquals(GrLang.RU, grLang(""))
        assertEquals(GrLang.RU, grLang("de"))
    }

    @Test fun decimals() {
        assertEquals("1${nnbsp}234,5", grDec(1234.5, lang = GrLang.RU))
        assertEquals("1,234.5", grDec(1234.5, lang = GrLang.EN))
        assertEquals("1,234.5", grDec(1234.5, lang = GrLang.ZH))
        assertEquals("1${nbsp}234,5", grDec(1234.5, lang = GrLang.PT))
        assertEquals("1${nbsp}234,5", grDec(1234.5, lang = GrLang.PL))
        assertEquals("1${nnbsp}234,5", grDec(1234.5, lang = GrLang.BE))
        assertEquals("−0.5", grDec(-0.5, lang = GrLang.EN))
        assertEquals("0.0", grDec(-0.01, lang = GrLang.EN))
        assertEquals("12", grDec(12.4, 0, GrLang.ZH))
        assertEquals(grDec(1234.5), grDec(1234.5, lang = GrLang.RU))
    }

    @Test fun ints() {
        for (lang in GrLang.entries) assertEquals(lang.name, "−7", grInt(-7, lang))
        assertEquals("−1${nnbsp}240", grInt(-1240, GrLang.RU))
        assertEquals("−1,240", grInt(-1240, GrLang.EN))
        assertEquals("1,240", grInt(1240, GrLang.ZH))
        assertEquals("12${nbsp}345", grInt(12345, GrLang.PT))
        assertEquals("12${nbsp}345", grInt(12345, GrLang.PL))
        assertEquals("12${nnbsp}345", grInt(12345, GrLang.BE))
        assertEquals("−7°", grDeg(-7, GrLang.EN))
    }

    @Test fun durations() {
        assertEquals("1 ч 12 мин", grDuration(72, GrLang.RU))
        assertEquals("1 h 12 min", grDuration(72, GrLang.EN))
        assertEquals("1小时12分钟", grDuration(72, GrLang.ZH))
        assertEquals("1 h 12 min", grDuration(72, GrLang.PT))
        assertEquals("1 godz. 12 min", grDuration(72, GrLang.PL))
        assertEquals("1 гадз 12 хв", grDuration(72, GrLang.BE))
        assertEquals("2 h", grDuration(120, GrLang.EN))
        assertEquals("45 min", grDuration(45, GrLang.EN))
        assertEquals("2小时", grDuration(120, GrLang.ZH))
        assertEquals("45分钟", grDuration(45, GrLang.ZH))
    }

    @Test fun dates() {
        val d = LocalDate.of(2026, 11, 16)
        assertEquals("Пн, 16 ноября 2026", grDate(d, GrLang.RU))
        assertEquals("Mon, 16 November 2026", grDate(d, GrLang.EN))
        assertEquals("2026年11月16日 周一", grDate(d, GrLang.ZH))
        assertEquals("seg., 16 de novembro de 2026", grDate(d, GrLang.PT))
        assertEquals("pon., 16 listopada 2026", grDate(d, GrLang.PL))
        assertEquals("Пн, 16 лістапада 2026", grDate(d, GrLang.BE))
        val sunday = LocalDate.of(2026, 3, 1)
        assertEquals("Sun, 1 March 2026", grDate(sunday, GrLang.EN))
        assertEquals("2026年3月1日 周日", grDate(sunday, GrLang.ZH))
        assertEquals("dom., 1 de março de 2026", grDate(sunday, GrLang.PT))
        assertEquals("niedz., 1 marca 2026", grDate(sunday, GrLang.PL))
        assertEquals("Нд, 1 сакавіка 2026", grDate(sunday, GrLang.BE))
    }

    @Test fun powerChips() {
        assertEquals("Включен · Ready", powerChip(2, GrLang.RU))
        assertEquals("On · Ready", powerChip(2, GrLang.EN))
        assertEquals("已上电 · Ready", powerChip(2, GrLang.ZH))
        assertEquals("Ligado · Ready", powerChip(2, GrLang.PT))
        assertEquals("Włączony · Ready", powerChip(2, GrLang.PL))
        assertEquals("Уключаны · Ready", powerChip(2, GrLang.BE))
        assertEquals("Off", powerChip(0, GrLang.EN))
        assertEquals("Engine running", engineChip(1240, GrLang.EN))
    }

    @Test fun cabinNotes() {
        assertEquals("Авто · 22,0°", cabinNote(true, true, 22, GrLang.RU))
        assertEquals("Auto · 22.0°", cabinNote(true, true, 22, GrLang.EN))
        assertEquals("自动 · 22.0°", cabinNote(true, true, 22, GrLang.ZH))
        assertEquals("Auto · 22,0°", cabinNote(true, true, 22, GrLang.PT))
        assertEquals("Auto · 22,0°", cabinNote(true, true, 22, GrLang.PL))
        assertEquals("Аўта · 22,0°", cabinNote(true, true, 22, GrLang.BE))
        assertEquals("Climate · 22.0°", cabinNote(false, true, 22, GrLang.EN))
        assertEquals("Ice risk", outsideNote(2, GrLang.EN))
    }

    @Test fun weekLabels() {
        fun week(lang: GrLang) = EnergyBuckets.build(emptyList(), ChartPeriod.WEEK, monday, lang).labels
        assertEquals(listOf("Вт", "Ср", "Чт", "Пт", "Сб", "Вс", "Пн"), week(GrLang.RU))
        assertEquals(listOf("Tue", "Wed", "Thu", "Fri", "Sat", "Sun", "Mon"), week(GrLang.EN))
        assertEquals(listOf("周二", "周三", "周四", "周五", "周六", "周日", "周一"), week(GrLang.ZH))
        assertEquals(listOf("Ter", "Qua", "Qui", "Sex", "Sáb", "Dom", "Seg"), week(GrLang.PT))
        assertEquals(listOf("Wt", "Śr", "Cz", "Pt", "So", "Nd", "Pn"), week(GrLang.PL))
        assertEquals(listOf("Аў", "Ср", "Чц", "Пт", "Сб", "Нд", "Пн"), week(GrLang.BE))
        assertEquals(week(GrLang.RU), EnergyBuckets.build(emptyList(), ChartPeriod.WEEK, monday).labels)
    }

    @Test fun pm25InEnglish() {
        assertEquals(
            listOf(Pm25Side("Cabin", 12, "excellent", Tone.ELECTRIC), Pm25Side("Outside", 35, "good", Tone.ELECTRIC)),
            pm25Sides(Pm25(inside = 12, outside = 35, levelIn = 1, levelOut = 2), GrLang.EN),
        )
        assertEquals("poor" to Tone.HEAT, pm25Quality(5, GrLang.EN))
    }

    @Test fun footerUnits() {
        assertEquals("3,500 kΩ", insulationText(3_500, GrLang.EN))
        assertEquals("12.5 MΩ", insulationText(12_480, GrLang.EN))
        assertEquals("3${nnbsp}500 кОм", insulationText(3_500, GrLang.BE))
        assertEquals("13.8", voltageText(13.84, GrLang.EN))
    }

    @Test fun todayCellsInEnglish() {
        val s = TodayStats(minutes = 72, avgKmh = 39, electricShare = 68, kwh = 8.4, litres = 2.1)
        val cells = todayCells(s, hasFuelTank = true, electric = Color.Green, primary = Color.White, lang = GrLang.EN)
        assertEquals(listOf("1 h 12 min", "68%", "39 km/h", "8.4 · 2.1"), cells.map { it.value })
        assertEquals(listOf("on the road", "electric", "avg speed", "kWh · l"), cells.map { it.label })
        val zh = todayCells(null, hasFuelTank = false, electric = Color.Green, primary = Color.White, lang = GrLang.ZH)
        assertEquals("千瓦时", zh.last().label)
    }

    @Test fun menuLabels() {
        assertEquals(R.drawable.gr_ic_car to "Car", menuItem("tech_panel", "x", GrLang.EN))
        assertEquals(R.drawable.gr_ic_flow to "Аўтаматызацыі", menuItem("automation", "x", GrLang.BE))
        assertEquals(R.drawable.gr_ic_sliders to "Ustawienia", menuItem("settings", "x", GrLang.PL))
        assertEquals(R.drawable.gr_ic_sliders to "Ledger", menuItem("ledger", "Ledger", GrLang.EN))
    }

    @Test fun lookTitles() {
        assertEquals(listOf("None", "Glass", "Metal", "Fine sand", "Concrete"), GrMaterial.entries.map { it.title(GrLang.EN) })
        assertEquals(listOf("Flat", "Convex", "Concave"), GrRelief.entries.map { it.title(GrLang.EN) })
        for (lang in GrLang.entries) {
            for (m in GrMaterial.entries) assertEquals(lang.name, false, m.title(lang).isBlank())
            for (r in GrRelief.entries) assertEquals(lang.name, false, r.title(lang).isBlank())
        }
        assertEquals(GrMaterial.entries.map { it.title }, GrMaterial.entries.map { it.title(GrLang.RU) })
        assertEquals(GrRelief.entries.map { it.title }, GrRelief.entries.map { it.title(GrLang.RU) })
    }

    @Test fun onlyRussianAndBelarusianUseCyrillic() {
        fun String.cyrillic() = any { it in 'Ѐ'..'ӿ' }
        for (lang in listOf(GrLang.EN, GrLang.ZH, GrLang.PT, GrLang.PL)) {
            val d = LocalDate.of(2026, 11, 16)
            val texts = listOfNotNull(
                grDate(d, lang), grDuration(72, lang), powerChip(2, lang), powerChip(0, lang), engineChip(1, lang),
                cabinNote(true, true, 22, lang), cabinNote(false, true, 22, lang), outsideNote(0, lang),
                insulationText(3_500, lang), insulationText(12_000, lang),
            ) + (1..4).mapNotNull { pm25Quality(it, lang)?.first } +
                GrMaterial.entries.map { it.title(lang) } + GrRelief.entries.map { it.title(lang) } +
                listOf("tech_panel", "trips", "charges", "automation", "settings").map { menuItem(it, "x", lang).second } +
                todayCells(null, true, Color.Green, Color.White, lang).flatMap { listOf(it.value, it.label) } +
                lang.words.let { listOf(it.km, it.kwh, it.litre, it.kmh, it.rpm, it.cabin, it.outside) }
            for (t in texts) assertEquals("$lang: $t", false, t.cyrillic())
        }
    }
}
