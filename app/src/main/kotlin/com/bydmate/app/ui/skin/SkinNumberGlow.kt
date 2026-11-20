package com.bydmate.app.ui.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

// fork: the neon glow of big numbers (Cockpit, Volt: SkinFonts.glow), a Compose text Shadow.

/** The glow's blur, in dp. */
private val NumberGlowBlur = 22.dp

/** The glow's alpha over the accent colour. */
private const val NUMBER_GLOW_ALPHA = 0.40f

/** The shadow behind a number of [size]: the accent [color] at 40 %, no offset, [blurPx] wide; null under 23 sp (RAJDHANI_MIN_SIZE). */
fun numberGlow(size: TextUnit, color: Color, blurPx: Float): Shadow? =
    if (size.isSp && size.value >= RAJDHANI_MIN_SIZE.value) Shadow(color.copy(alpha = NUMBER_GLOW_ALPHA), Offset.Zero, blurPx) else null

/** True when a text may glow: the kit's [glow] is on and it is a Monospace text without letters (numberFont's test). */
fun glowsNumber(glow: Boolean, family: FontFamily?, styleFamily: FontFamily?, text: CharSequence): Boolean =
    glow && (family ?: styleFamily) == FontFamily.Monospace && isNumericOnly(text)

/**
 * [style] with the glow when this text glows and is big; else [style] itself (no allocation). The
 * shadow is remembered per colour, size and density, so it is an equal value across recompositions.
 * A shadow the caller already set stays.
 */
@Composable
internal fun glowStyle(style: TextStyle, glow: Boolean, family: FontFamily?, text: CharSequence, size: TextUnit, color: Color): TextStyle {
    if (!glow || style.shadow != null || !glowsNumber(true, family, style.fontFamily, text)) return style
    val density = LocalDensity.current
    val shadow = remember(color, size, density.density) { numberGlow(size, color, with(density) { NumberGlowBlur.toPx() }) } ?: return style
    return style.copy(shadow = shadow)
}
