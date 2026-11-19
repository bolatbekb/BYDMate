package com.bydmate.app.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.SkinPalette
import kotlin.math.roundToInt

// fork: what each control style looks like, as plain values. SkinCard, SkinSwitch, SkinSlider,
// SkinDivider and SkinAlertDialog draw these; the unit tests check them. A new style is one more
// branch in each `when` below.

/** How a styled card outlines itself. */
enum class CardBorderKind { NONE, SOLID, TOP_HIGHLIGHT }

/**
 * A card in one style. The default value is MATERIAL: the caller's arguments as they are.
 *
 * [containerAlpha] multiplies the alpha of the caller's container colour. [borderColor] is the
 * SOLID colour, or the top of the TOP_HIGHLIGHT gradient that fades to [borderFadeColor].
 * [flat] drops the elevation. [padding] is the layout padding the style adds: none ever does,
 * the stripe is drawn over the card's start edge, not beside it.
 */
data class CardLook(
    val containerAlpha: Float = 1f,
    val border: CardBorderKind = CardBorderKind.NONE,
    val borderColor: Color = Color.Unspecified,
    val borderFadeColor: Color = Color.Unspecified,
    val stripe: Boolean = false,
    val stripeColor: Color = Color.Unspecified,
    val stripeWidth: Dp = 0.dp,
    val flat: Boolean = false,
    val padding: Dp = 0.dp,
) {
    /** The M3 card with the caller's arguments, untouched. */
    val passThrough: Boolean get() = this == CardLook()
}

val CardBorderWidth: Dp = 1.dp
val CardStripeWidth: Dp = 3.dp
const val GLASS_ALPHA = 0.7f

fun cardLook(style: CardStyle, palette: SkinPalette): CardLook = when (style) {
    CardStyle.MATERIAL -> CardLook()
    CardStyle.OUTLINED -> CardLook(border = CardBorderKind.SOLID, borderColor = palette.cardBorder)
    CardStyle.FLAT -> CardLook(flat = true)
    CardStyle.GLASS -> CardLook(
        containerAlpha = GLASS_ALPHA,
        border = CardBorderKind.TOP_HIGHLIGHT,
        borderColor = palette.textPrimary.copy(alpha = 0.22f),
        borderFadeColor = palette.cardBorder.copy(alpha = 0.3f),
    )
    CardStyle.STRIPE -> CardLook(stripe = true, stripeColor = palette.accentGreen, stripeWidth = CardStripeWidth)
    // a shadow would show through a see-through card, so it is flat as well
    CardStyle.BORDER_ONLY -> CardLook(
        containerAlpha = 0f,
        border = CardBorderKind.SOLID,
        borderColor = palette.cardBorder,
        flat = true,
    )
}

/** A card that is a dialog's surface: see-through would show the screen behind the scrim, so it stays solid. */
fun CardLook.inDialog(): CardLook = copy(containerAlpha = 1f)

/** OUTLINED, BORDER_ONLY and STRIPE outline the AlertDialog surface with a 1 dp CardBorder. */
fun dialogHasBorder(style: CardStyle): Boolean = when (style) {
    CardStyle.OUTLINED, CardStyle.BORDER_ONLY, CardStyle.STRIPE -> true
    CardStyle.MATERIAL, CardStyle.FLAT, CardStyle.GLASS -> false
}

/** How HorizontalDivider draws under a card style. */
enum class DividerLook { AS_GIVEN, FADED_ENDS, CARD_BORDER }

fun dividerLook(style: CardStyle): DividerLook = when (style) {
    CardStyle.GLASS -> DividerLook.FADED_ENDS
    CardStyle.BORDER_ONLY -> DividerLook.CARD_BORDER
    CardStyle.MATERIAL, CardStyle.OUTLINED, CardStyle.FLAT, CardStyle.STRIPE -> DividerLook.AS_GIVEN
}

/**
 * A styled switch face inside the M3 box of 52 x 32 dp.
 *
 * [stateColor] says on or off: AccentGreen on, CardBorder off. It fills the track when
 * [trackFilled], otherwise it strokes the track [trackStroke] wide. [thumbColor] is what the
 * thumb shows: its fill, or its ring when [thumbRing] > 0 (then [thumbFill] is what is inside the
 * ring). [thumbInset] is the gap between a track end and the thumb. [alpha] is 0.38 when disabled,
 * as M3 does.
 */
data class SwitchLook(
    val stateColor: Color,
    val trackFilled: Boolean,
    val trackStroke: Dp,
    val trackHeight: Dp,
    val trackCorner: Dp,
    val thumbColor: Color,
    val thumbFill: Color,
    val thumbRing: Dp,
    val thumbSize: Dp,
    val thumbCorner: Dp,
    val thumbInset: Dp,
    val alpha: Float,
)

val SwitchWidth: Dp = 52.dp
val SwitchHeight: Dp = 32.dp
const val SWITCH_DISABLED_ALPHA = 0.38f

/** The face of [style]; null for MATERIAL, which M3 draws itself. */
fun switchLook(style: SwitchStyle, checked: Boolean, enabled: Boolean, palette: SkinPalette): SwitchLook? {
    val state = if (checked) palette.accentGreen else palette.cardBorder
    // the thumb stays readable on the dim off track
    val mark = if (checked) palette.accentGreen else palette.textSecondary
    val alpha = if (enabled) 1f else SWITCH_DISABLED_ALPHA
    return when (style) {
        SwitchStyle.MATERIAL -> null
        SwitchStyle.SQUARE -> {
            val thumb = if (checked) palette.navyMid else palette.textSecondary
            SwitchLook(
                stateColor = state, trackFilled = true, trackStroke = 0.dp, trackHeight = SwitchHeight,
                trackCorner = 2.dp, thumbColor = thumb, thumbFill = thumb, thumbRing = 0.dp,
                thumbSize = 24.dp, thumbCorner = 2.dp, thumbInset = 4.dp, alpha = alpha,
            )
        }
        SwitchStyle.PILL -> SwitchLook(
            stateColor = state, trackFilled = true, trackStroke = 0.dp, trackHeight = 4.dp,
            trackCorner = 2.dp, thumbColor = mark, thumbFill = palette.navyMid, thumbRing = 2.dp,
            thumbSize = 20.dp, thumbCorner = 10.dp, thumbInset = 0.dp, alpha = alpha,
        )
        SwitchStyle.OUTLINE -> SwitchLook(
            stateColor = state, trackFilled = false, trackStroke = 2.dp, trackHeight = SwitchHeight,
            trackCorner = 16.dp, thumbColor = mark, thumbFill = Color.Transparent, thumbRing = 2.dp,
            thumbSize = 20.dp, thumbCorner = 10.dp, thumbInset = 6.dp, alpha = alpha,
        )
    }
}

/** Left edge of the thumb in a track [width] wide: off at the start, on at the end; mirrored in RTL. */
fun switchThumbX(width: Float, thumb: Float, inset: Float, fraction: Float, rtl: Boolean): Float {
    val travel = (width - thumb - inset * 2).coerceAtLeast(0f)
    val f = if (rtl) 1f - fraction else fraction
    return inset + travel * f
}

const val SLIDER_SEGMENTS = 12

/** Where [value] sits in [range], 0..1; an empty range gives 0. */
fun sliderFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f) return 0f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

/** How many of the [SLIDER_SEGMENTS] segments are lit at [fraction]. */
fun litSegments(fraction: Float): Int =
    (fraction.coerceIn(0f, 1f) * SLIDER_SEGMENTS).roundToInt().coerceIn(0, SLIDER_SEGMENTS)
