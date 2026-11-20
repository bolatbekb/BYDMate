package com.bydmate.app.ui.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.SkinPalette
import kotlin.math.roundToInt

// fork: what each control style looks like, as plain values. SkinCard, SkinSwitch, SkinSlider,
// SkinDivider, SkinAlertDialog and SkinNavigationBar draw these; the unit tests check them. A new
// style is one more branch in each `when` below.

/**
 * How a styled card outlines itself. GRAPHITE_EDGE is the 1 px edge of the Graphite home's raised
 * key (tabActiveEdge of the live scheme), which SkinCard reads from GraphiteLookState.
 */
enum class CardBorderKind { NONE, SOLID, TOP_HIGHLIGHT, GRAPHITE_EDGE }

/**
 * A card in one style. The default value is MATERIAL: the caller's arguments as they are.
 *
 * [containerAlpha] multiplies the alpha of the caller's container colour. [borderColor] is the
 * SOLID colour, or the top of the TOP_HIGHLIGHT gradient that fades to [borderFadeColor].
 * [flat] drops the elevation. [padding] is the layout padding the style adds: none ever does,
 * the stripe is drawn over the card's start edge, not beside it. [graphite] puts the Graphite
 * home's card (grCard: fill, material, relief, 2.5D edges, contour and shadow) under a see-through
 * M3 container (GraphiteSkinParts.kt).
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
    val graphite: Boolean = false,
) {
    /** The M3 card with the caller's arguments, untouched. */
    val passThrough: Boolean get() = this == MATERIAL

    companion object {
        /** The MATERIAL look, one shared value: a Classic card allocates none. */
        val MATERIAL = CardLook()
    }
}

val CardBorderWidth: Dp = 1.dp
val CardStripeWidth: Dp = 3.dp
const val GLASS_ALPHA = 0.7f

/**
 * The card of [style]. A [nested] card sits inside another skinned card (LocalSkinCardDepth ≥ 1)
 * and does not repeat what the outer one already draws:
 * - GRAPHITE: the caller's own opaque container with the raised key's thin edge
 *   (GRAPHITE_EDGE); no shadow, material, relief or glare;
 * - STRIPE: no second bar, the plain M3 card;
 * - GLASS: the caller's container, solid, so the see-through layers do not stack;
 * - the other styles nest as they are (a border inside a border, a flat card in a flat one).
 */
fun cardLook(style: CardStyle, palette: SkinPalette, nested: Boolean = false): CardLook = when (style) {
    CardStyle.MATERIAL -> CardLook.MATERIAL
    CardStyle.OUTLINED -> CardLook(border = CardBorderKind.SOLID, borderColor = palette.cardBorder)
    CardStyle.FLAT -> CardLook(flat = true)
    CardStyle.GLASS -> CardLook(
        containerAlpha = if (nested) 1f else GLASS_ALPHA,
        border = CardBorderKind.TOP_HIGHLIGHT,
        borderColor = palette.textPrimary.copy(alpha = 0.22f),
        borderFadeColor = palette.cardBorder.copy(alpha = 0.3f),
    )
    CardStyle.STRIPE -> if (nested) {
        CardLook.MATERIAL
    } else {
        CardLook(stripe = true, stripeColor = palette.accentGreen, stripeWidth = CardStripeWidth)
    }
    // a shadow would show through a see-through card, so it is flat as well
    CardStyle.BORDER_ONLY -> CardLook(
        containerAlpha = 0f,
        border = CardBorderKind.SOLID,
        borderColor = palette.cardBorder,
        flat = true,
    )
    // grCard draws the fill, the contour and its own shadow; inside it, the raised key of the home
    CardStyle.GRAPHITE -> if (nested) {
        CardLook(border = CardBorderKind.GRAPHITE_EDGE, flat = true)
    } else {
        CardLook(containerAlpha = 0f, flat = true, graphite = true)
    }
}

/**
 * A card that is a dialog's surface: see-through would show the screen behind the scrim, so it
 * stays solid. The Graphite card already is: its fill is opaque, under the see-through container.
 */
fun CardLook.inDialog(): CardLook = if (graphite) this else copy(containerAlpha = 1f)

/** OUTLINED, BORDER_ONLY and STRIPE outline the AlertDialog surface with a 1 dp CardBorder. */
fun dialogHasBorder(style: CardStyle): Boolean = when (style) {
    CardStyle.OUTLINED, CardStyle.BORDER_ONLY, CardStyle.STRIPE -> true
    CardStyle.MATERIAL, CardStyle.FLAT, CardStyle.GLASS -> false
    CardStyle.GRAPHITE -> false // the Graphite card draws its own contour
}

/** How HorizontalDivider draws under a card style. */
enum class DividerLook { AS_GIVEN, FADED_ENDS, CARD_BORDER }

fun dividerLook(style: CardStyle): DividerLook = when (style) {
    CardStyle.GLASS -> DividerLook.FADED_ENDS
    CardStyle.BORDER_ONLY -> DividerLook.CARD_BORDER
    // the Graphite home's dividers are plain lines too
    CardStyle.MATERIAL, CardStyle.OUTLINED, CardStyle.FLAT, CardStyle.STRIPE, CardStyle.GRAPHITE -> DividerLook.AS_GIVEN
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

/** How much of the accent lights the GRAPHITE groove when on. */
const val GRAPHITE_GROOVE_LIT = 0.3f

/** The face of [style]; null for MATERIAL, which M3 draws itself. */
fun switchLook(style: SwitchStyle, checked: Boolean, enabled: Boolean, palette: SkinPalette): SwitchLook? {
    // fork: Volt's thin PILL track would vanish in the dim cardBorder when off, so it takes textMuted
    val off = if (style == SwitchStyle.PILL) palette.textMuted else palette.cardBorder
    val state = if (checked) palette.accentGreen else off
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
        // a sunken groove (Inset) lit by the accent when on, a raised round thumb; the Graphite
        // rims and relief over them are drawn by GraphiteSwitchFace
        SwitchStyle.GRAPHITE -> {
            val thumb = if (checked) palette.accentGreen else palette.textMuted
            SwitchLook(
                stateColor = if (checked) lerp(palette.navyMid, palette.accentGreen, GRAPHITE_GROOVE_LIT) else palette.navyMid,
                trackFilled = true, trackStroke = 0.dp, trackHeight = SwitchHeight, trackCorner = 16.dp,
                thumbColor = thumb, thumbFill = thumb, thumbRing = 0.dp,
                thumbSize = 24.dp, thumbCorner = 12.dp, thumbInset = 4.dp, alpha = alpha,
            )
        }
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

/**
 * A key of the bottom bar in one style (navLook). The default value is MATERIAL: the M3 item with
 * the caller's arguments.
 *
 * [lit] says the key shows its indicator: the selected key (GRAPHITE: also a pressed one, as on the
 * Graphite home). [fill] and [edge] colour the CUT plate, the UNDERLINE bar and the OUTLINE ring
 * ([edge] is [NavEdgeWidth] wide). [content] is the GRAPHITE key's icon and label colour; the other
 * styles keep the caller's `colors`.
 */
data class NavLook(
    val style: NavStyle = NavStyle.MATERIAL,
    val lit: Boolean = false,
    val fill: Color = Color.Unspecified,
    val edge: Color = Color.Unspecified,
    val content: Color = Color.Unspecified,
) {
    /** The M3 item with the caller's arguments, untouched. */
    val passThrough: Boolean get() = this == MATERIAL

    companion object {
        /** The MATERIAL look, one shared value: a Classic key allocates none. */
        val MATERIAL = NavLook()
    }
}

/** M3's indicator around a 24 dp icon is 64 x 32 dp: 20 dp on each side, 4 dp above and below. */
val NavIndicatorPadX: Dp = 20.dp
val NavIndicatorPadY: Dp = 4.dp
val NavCutCorner: Dp = 8.dp
val NavUnderlineHeight: Dp = CardStripeWidth
val NavEdgeWidth: Dp = CardBorderWidth

/**
 * The key of [style], [selected] or not, in [palette]:
 * - GRAPHITE: the Graphite home's menu key; lit, it is the raised key with the glowing bar and
 *   textPrimary, else textSecondary (as the Graphite home's own menu had it);
 * - CUT (Cockpit): a plate with cut corners in navIndicator with an accent edge;
 * - UNDERLINE (Telemetry): an accent bar along the bottom of the indicator;
 * - OUTLINE (OLED): the M3 pill as an accent ring, no fill.
 */
fun navLook(style: NavStyle, selected: Boolean, palette: SkinPalette): NavLook = when (style) {
    NavStyle.MATERIAL -> NavLook.MATERIAL
    NavStyle.GRAPHITE -> NavLook(style, selected, content = if (selected) palette.textPrimary else palette.textSecondary)
    NavStyle.CUT -> NavLook(style, selected, fill = palette.navIndicator, edge = palette.accentGreen)
    NavStyle.UNDERLINE -> NavLook(style, selected, fill = palette.accentGreen)
    NavStyle.OUTLINE -> NavLook(style, selected, fill = Color.Transparent, edge = palette.accentGreen)
}

/** Where a key of the GRAPHITE dock sits in its row (px, left to right) and whether it is lit. */
data class NavKeySpan(val left: Float, val right: Float, val lit: Boolean)

/**
 * The x of each divider of the GRAPHITE dock: the middle of the gap between two neighbouring keys,
 * none beside a lit key (the Graphite home hides the dividers next to its lit key).
 */
fun navDividerXs(keys: Collection<NavKeySpan>): List<Float> =
    keys.sortedBy { it.left }.zipWithNext().filter { (a, b) -> !a.lit && !b.lit }.map { (a, b) -> (a.right + b.left) / 2f }
