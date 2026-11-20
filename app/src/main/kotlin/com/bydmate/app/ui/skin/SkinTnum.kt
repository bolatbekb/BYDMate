package com.bydmate.app.ui.skin

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

// fork: the tabular-digit helpers of the skin Text (SkinNumbers.kt), moved out unchanged.

/**
 * Fonts whose digits are made tabular here: Inter's and Exo 2's are proportional by default and
 * have a tnum feature; the system font (Roboto) is tabular, and tnum keeps any other system font
 * still. Roboto Condensed's digits are tabular already; Rajdhani has no tnum (see numberFont).
 */
internal fun SkinFont.needsTnum(): Boolean = this == SkinFont.INTER || this == SkinFont.EXO2 || this == SkinFont.SYSTEM

/** The family a numeric [SkinFont] draws in: SYSTEM is the platform default (Roboto), never the caller's Monospace. */
internal fun SkinFont.numberFamily(): FontFamily = family() ?: FontFamily.Default

/** [have] (a style's feature settings) with "tnum" in it exactly once. */
internal fun tnumSettings(have: String?): String = when {
    have.isNullOrEmpty() -> "tnum"
    have.split(',').any { it.trim().startsWith("tnum") } -> have
    else -> "$have, tnum"
}

internal fun TextStyle.withTnum(): TextStyle {
    val have = fontFeatureSettings
    val want = tnumSettings(have)
    return if (want == have) this else merge(TextStyle(fontFeatureSettings = want))
}
