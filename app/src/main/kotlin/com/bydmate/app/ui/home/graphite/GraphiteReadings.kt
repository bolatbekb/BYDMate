package com.bydmate.app.ui.home.graphite

import com.bydmate.app.data.autoservice.Pm25
import kotlin.math.roundToInt

/* The battery and air readings as the cards print them: PM2.5 grades, SOH, voltage, insulation. */

/** A palette accent named without the palette, so a rule can pick it in a unit test. */
internal enum class Tone { ELECTRIC, FUEL, HEAT }

/** PM2.5 grade 1..6 (the SDK's levels) to its word and colour; null outside the grades. */
internal fun pm25Quality(level: Int?, lang: GrLang = GrLang.RU): Pair<String, Tone>? {
    val words = lang.words.pm25
    return when (level) {
        1 -> words[0] to Tone.ELECTRIC
        2 -> words[1] to Tone.ELECTRIC
        3 -> words[2] to Tone.FUEL
        4, 5, 6 -> words[3] to Tone.HEAT
        else -> null
    }
}

internal data class Pm25Side(val label: String, val value: Int, val quality: String, val tone: Tone)

/** The sides of the PM2.5 line, cabin first; a side needs a value and a grade. Null when none. */
internal fun pm25Sides(pm: Pm25?, lang: GrLang = GrLang.RU): List<Pm25Side>? {
    if (pm == null) return null
    val sides = listOfNotNull(
        pm25Side(lang.words.cabin, pm.inside, pm.levelIn, lang),
        pm25Side(lang.words.outside, pm.outside, pm.levelOut, lang),
    )
    return sides.ifEmpty { null }
}

private fun pm25Side(label: String, value: Int?, level: Int?, lang: GrLang): Pm25Side? {
    val (word, tone) = pm25Quality(level, lang) ?: return null
    return Pm25Side(label, value ?: return null, word, tone)
}

internal fun sohText(soh: Float): String = "${soh.roundToInt()} %"

internal fun voltageText(volts: Double, lang: GrLang = GrLang.RU): String = grDec(volts, lang = lang)

/** kΩ as «3 500 кОм»; from 10 000 kΩ on, «12,5 МОм». */
internal fun insulationText(kohm: Int, lang: GrLang = GrLang.RU): String =
    if (kohm >= 10_000) grDec(kohm / 1000.0, lang = lang) + " " + lang.words.mohm else grInt(kohm, lang) + " " + lang.words.kohm
