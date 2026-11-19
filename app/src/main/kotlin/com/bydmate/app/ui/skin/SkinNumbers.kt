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

/** True when [text] holds no letters: digits, spaces and punctuation only (what Rajdhani can draw). */
fun isNumericOnly(text: CharSequence): Boolean {
    for (i in 0 until text.length) if (Character.isLetter(text[i])) return false
    return true
}

/**
 * The font that takes the place of a Monospace text, or null when the text stays as the caller
 * wrote it. [family] is the fontFamily argument, [styleFamily] the one in the style (the
 * argument wins, as in M3). A text with letters is never replaced.
 */
fun numberFont(numeric: SkinFont?, family: FontFamily?, styleFamily: FontFamily?, text: CharSequence): SkinFont? {
    if (numeric == null) return null
    if ((family ?: styleFamily) != FontFamily.Monospace) return null
    if (!isNumericOnly(text)) return null
    return numeric
}

/** Fonts whose digits are made tabular here (Inter's are proportional by default; the others' are tabular). */
internal fun SkinFont.needsTnum(): Boolean = this == SkinFont.INTER

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
    M3Text(
        text, modifier, color, fontSize, fontStyle, fontWeight,
        if (font == null) fontFamily else font.family(),
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
    M3Text(
        text, modifier, color, fontSize, fontStyle, fontWeight,
        if (font == null) fontFamily else font.family(),
        letterSpacing, textDecoration, textAlign, lineHeight, overflow, softWrap, maxLines, minLines,
        inlineContent, onTextLayout,
        if (font != null && font.needsTnum()) style.withTnum() else style,
    )
}
