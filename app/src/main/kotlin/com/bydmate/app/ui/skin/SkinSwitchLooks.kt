package com.bydmate.app.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.SkinPalette

// fork: the faces of the styled switches that switchLook (SkinControlStyle.kt) hands out, one
// function each.

/** Telemetry's: a filled rectangle with 2 dp corners. */
internal fun squareSwitch(state: Color, checked: Boolean, alpha: Float, palette: SkinPalette): SwitchLook {
    val thumb = if (checked) palette.navyMid else palette.textSecondary
    return SwitchLook(
        stateColor = state, trackFilled = true, trackStroke = 0.dp, trackHeight = SwitchHeight,
        trackCorner = 2.dp, thumbColor = thumb, thumbFill = thumb, thumbRing = 0.dp,
        thumbSize = 24.dp, thumbCorner = 2.dp, thumbInset = 4.dp, alpha = alpha,
    )
}

/** The Graphite home's groove and thumb. */
internal fun graphiteSwitch(checked: Boolean, alpha: Float, palette: SkinPalette): SwitchLook {
    val thumb = if (checked) palette.accentGreen else palette.textMuted
    return SwitchLook(
        stateColor = if (checked) lerp(palette.navyMid, palette.accentGreen, GRAPHITE_GROOVE_LIT) else palette.navyMid,
        trackFilled = true, trackStroke = 0.dp, trackHeight = SwitchHeight, trackCorner = 16.dp,
        thumbColor = thumb, thumbFill = thumb, thumbRing = 0.dp,
        thumbSize = 24.dp, thumbCorner = 12.dp, thumbInset = 4.dp, alpha = alpha,
    )
}

/** Cockpit: a square-cornered 52 x 28 outline, a block thumb, a soft glow around it when on and enabled (a faded glow would be cut to the box). */
internal fun hudSwitch(checked: Boolean, enabled: Boolean, alpha: Float, palette: SkinPalette): SwitchLook {
    val c = if (checked) palette.accentGreen else palette.textMuted
    return SwitchLook(
        stateColor = c, trackFilled = false, trackStroke = 1.dp, trackHeight = 28.dp,
        trackCorner = 0.dp, thumbColor = c, thumbFill = c, thumbRing = 0.dp,
        thumbSize = 20.dp, thumbCorner = 0.dp, thumbInset = 3.dp, alpha = alpha, thumbWidth = 22.dp,
        trackFill = if (checked) palette.accentGreen.copy(alpha = 0.12f) else Color.Transparent,
        glow = if (checked && enabled) palette.accentGreen.copy(alpha = 0.5f) else Color.Unspecified,
        glowSpread = 16.dp, glowOnThumb = true,
    )
}

/** Graphite Pro: a filled pill, a round white thumb with a 1 dp dark shadow. */
internal fun iosSwitch(checked: Boolean, alpha: Float, palette: SkinPalette): SwitchLook {
    val thumb = if (checked) Color.White else Color(0xFF9AA0A8)
    return SwitchLook(
        stateColor = if (checked) palette.accentGreen else palette.cardBorder,
        trackFilled = true, trackStroke = 0.dp, trackHeight = SwitchHeight, trackCorner = 16.dp,
        thumbColor = thumb, thumbFill = thumb, thumbRing = 0.dp,
        thumbSize = 26.dp, thumbCorner = 13.dp, thumbInset = 3.dp, alpha = alpha,
        thumbShadow = Color.Black.copy(alpha = 0.35f),
    )
}

/** Volt: a filled pill with a glow around the track when on and enabled. */
internal fun glowSwitch(checked: Boolean, enabled: Boolean, alpha: Float, palette: SkinPalette): SwitchLook {
    val thumb = if (checked) palette.navyDark else palette.textSecondary
    return SwitchLook(
        stateColor = if (checked) palette.accentGreen else palette.textMuted.copy(alpha = 0.35f),
        trackFilled = true, trackStroke = 0.dp, trackHeight = SwitchHeight, trackCorner = 16.dp,
        thumbColor = thumb, thumbFill = thumb, thumbRing = 0.dp,
        thumbSize = 26.dp, thumbCorner = 13.dp, thumbInset = 3.dp, alpha = alpha,
        glow = if (checked && enabled) palette.accentGreen.copy(alpha = 0.45f) else Color.Unspecified,
        glowSpread = 14.dp,
    )
}
