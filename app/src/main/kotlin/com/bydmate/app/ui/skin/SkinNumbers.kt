package com.bydmate.app.ui.skin

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.Text as M3Text

// fork: drop-ins for the M3 Text (both overloads, same names, parameters and defaults as M3 1.3.1).
// The author sets FontFamily.Monospace on numbers; when the skin's kit has a numeric font
// (SkinFonts.numeric) that family is replaced, and nothing else: weight, size, colour stay the
// caller's. Under Classic (numeric == null) the wrapper changes nothing: one SkinState read,
// no remember, no allocation, one M3 call with the caller's own arguments.
//
// Only a text without letters (digits, signs, punctuation) takes the skin's numeric font. A
// Monospace text with letters (an address, a URL, a code, «12,5 км») keeps the author's Monospace
// exactly: it is not a number, and Rajdhani has no Cyrillic anyway.
//
// The author uses Monospace so that values stay still and columns line up, so the digits of the
// replacement are tabular: Inter and Exo 2 have proportional digits and a tnum feature, which is
// turned on; Roboto Condensed's digits are tabular already. Rajdhani's digits are proportional with
// no tnum, and its only file is Medium: as on the Graphite home (grStyle), it draws big light
// numerals only (RAJDHANI_MIN_SIZE and up, weight Medium or lighter), and every other number takes
// the system font with tnum, which also never synthesises a bold.

/** True when [text] holds no letters: digits, spaces and punctuation only (what Rajdhani can draw). */
fun isNumericOnly(text: CharSequence): Boolean {
    for (i in 0 until text.length) if (Character.isLetter(text[i])) return false
    return true
}

/** Rajdhani draws numerals this big or bigger: 46 mockup px on the Graphite home (NUMERAL_MIN_SIZE). */
val RAJDHANI_MIN_SIZE: TextUnit = 23.sp

/** The heaviest weight Rajdhani draws without a synthetic bold: its only file is Medium. */
val RAJDHANI_MAX_WEIGHT: FontWeight = FontWeight.Medium

/**
 * True when Rajdhani may draw a number of [size] and [weight] (the effective ones: the argument,
 * else the style's): big (sp, [RAJDHANI_MIN_SIZE] and up) and Medium or lighter, null being Normal.
 */
fun rajdhaniFits(size: TextUnit, weight: FontWeight?): Boolean =
    size.isSp && size.value >= RAJDHANI_MIN_SIZE.value && (weight ?: FontWeight.Normal) <= RAJDHANI_MAX_WEIGHT

/**
 * The font that takes the place of a Monospace text, or null when the text stays as the caller
 * wrote it. [family] is the fontFamily argument, [styleFamily] the one in the style (the
 * argument wins, as in M3). A text with letters is never replaced. The size and weight then
 * pick the font for this text: [forNumber].
 */
fun numberFont(numeric: SkinFont?, family: FontFamily?, styleFamily: FontFamily?, text: CharSequence): SkinFont? {
    if (numeric == null) return null
    if ((family ?: styleFamily) != FontFamily.Monospace) return null
    if (!isNumericOnly(text)) return null
    return numeric
}

/**
 * This numeric font for a number of [size] and [weight], the effective ones (the argument, else
 * the style's): RAJDHANI keeps only big light numerals (rajdhaniFits) and gives the others to
 * SYSTEM; every other font takes any size and weight.
 */
fun SkinFont.forNumber(size: TextUnit, weight: FontWeight?): SkinFont =
    if (this == SkinFont.RAJDHANI && !rajdhaniFits(size, weight)) SkinFont.SYSTEM else this

/**
 * Fonts whose digits are made tabular here: Inter's and Exo 2's are proportional by default and
 * have a tnum feature; the system font (Roboto) is tabular, and tnum keeps any other system font
 * still. Roboto Condensed's digits are tabular already; Rajdhani has no tnum (see numberFont).
 */
internal fun SkinFont.needsTnum(): Boolean = this == SkinFont.INTER || this == SkinFont.EXO2 || this == SkinFont.SYSTEM

/** The family a numeric [SkinFont] draws in: SYSTEM is the platform default (Roboto), never the caller's Monospace. */
private fun SkinFont.numberFamily(): FontFamily = family() ?: FontFamily.Default

/** [have] (a style's feature settings) with "tnum" in it exactly once. */
internal fun tnumSettings(have: String?): String = when {
    have.isNullOrEmpty() -> "tnum"
    have.split(',').any { it.trim().startsWith("tnum") } -> have
    else -> "$have, tnum"
}

private fun TextStyle.withTnum(): TextStyle {
    val have = fontFeatureSettings
    val want = tnumSettings(have)
    return if (want == have) this else merge(TextStyle(fontFeatureSettings = want))
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val font = numberFont(SkinState.current.kit.fonts.numeric, fontFamily, style.fontFamily, text)
        ?.forNumber(if (fontSize.isSpecified) fontSize else style.fontSize, fontWeight ?: style.fontWeight)
    M3Text(
        text, modifier, color, fontSize, fontStyle, fontWeight,
        if (font == null) fontFamily else font.numberFamily(),
        letterSpacing, textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines,
        onTextLayout,
        if (font != null && font.needsTnum()) style.withTnum() else style,
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    val font = numberFont(SkinState.current.kit.fonts.numeric, fontFamily, style.fontFamily, text.text)
        ?.forNumber(if (fontSize.isSpecified) fontSize else style.fontSize, fontWeight ?: style.fontWeight)
    M3Text(
        text, modifier, color, fontSize, fontStyle, fontWeight,
        if (font == null) fontFamily else font.numberFamily(),
        letterSpacing, textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines,
        inlineContent, onTextLayout,
        if (font != null && font.needsTnum()) style.withTnum() else style,
    )
}
