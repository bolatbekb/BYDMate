package com.bydmate.app.ui.skin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.home.graphite.GrPalette
import com.bydmate.app.ui.home.graphite.highlight
import com.bydmate.app.ui.theme.SkinPalette

// fork: what the buttons, the filter chip, the outlined text field, the checkbox and the radio
// button look like in each style, as plain values. SkinButtons, SkinChip, SkinTextField and
// SkinChecks draw these; the unit tests check them. The buttons, the chip and the field follow the
// card style (SkinControls.card): its border, container and indicator; the checks have their own
// style (SkinControls.check). The default value of each look is MATERIAL, the caller's arguments
// as they are. The caller's text, sizes, padding and content always stay the caller's.
//
// The drafts (Cockpit, Graphite Pro, Volt, Telemetry, OLED) get modest variants here; their final
// design is stage 4.

/** The three M3 buttons: Button (filled), OutlinedButton and TextButton. */
enum class ButtonKind { FILLED, OUTLINED, TEXT }

/**
 * A button in one style.
 * - [pill]: the Graphite home's pill (grPill) in the caller's container colour, under a see-through
 *   M3 container;
 * - [containerAlpha] multiplies the alpha of the caller's container colours;
 * - [border] ([borderColor], [borderFadeColor] as in CardLook) takes the place of a border the
 *   caller left unset: the M3 default, or one that shows nothing (styledBorder);
 * - [flat] drops the elevation;
 * - [contentColor] takes the place of the content colour when the caller left M3's own colours;
 * - [gradientStart] to [gradientEnd] (Volt's filled button): a horizontal gradient fill under a
 *   see-through M3 container, with a [glow] [glowSpread] wide behind it.
 */
data class ButtonLook(
    val pill: Boolean = false,
    val containerAlpha: Float = 1f,
    val border: CardBorderKind = CardBorderKind.NONE,
    val borderColor: Color = Color.Unspecified,
    val borderFadeColor: Color = Color.Unspecified,
    val flat: Boolean = false,
    val contentColor: Color = Color.Unspecified,
    val gradientStart: Color = Color.Unspecified,
    val gradientEnd: Color = Color.Unspecified,
    val glow: Color = Color.Unspecified,
    val glowSpread: Dp = 0.dp,
) {
    /** The M3 button with the caller's arguments, untouched. */
    val passThrough: Boolean get() = this == MATERIAL

    val gradient: Boolean get() = gradientStart.isSpecified

    /** The M3 container is see-through and a fill is drawn under it. */
    val underlay: Boolean get() = pill || gradient

    companion object {
        /** The MATERIAL look, one shared value: a Classic button allocates none. */
        val MATERIAL = ButtonLook()
    }
}

/**
 * The [kind] of button under the card [style], in [palette]; [fill] is the caller's container
 * colour in the button's state ([enabledFill]: the same when enabled). A filled button keeps its fill in every style: the author's
 * buttons colour their text for that fill (NavyDark on AccentGreen), often on the Text itself.
 * - GRAPHITE: the filled button is the Graphite home's pill in its own fill (a see-through one
 *   stays as M3 draws it); the outlined one takes the raised key's edge; the text one the
 *   Graphite text colour; all flat, as the Graphite card;
 * - OUTLINED (Graphite Pro) and BORDER_ONLY (OLED): the outlined button is an outline only (no
 *   container); BORDER_ONLY is flat, as its cards; the cut corners come with the shape;
 * - BRACKETS (Cockpit): the filled button flat; the outlined one an outline of cardBorder;
 * - GLASS (Volt): the filled button in the skin accent ([enabledFill] is accentGreen, M3's default) a
 *   gradient accentBlue to accentGreen with a glow, under a see-through container; any other
 *   filled button keeps its fill, flat; the outlined button the glass card's top highlight edge and a
 *   see-through container; the text one M3's own;
 * - FLAT: no elevation;
 * - STRIPE (Telemetry): the filled button flat; the others M3's own, with the skin's corners.
 */
fun buttonLook(style: CardStyle, kind: ButtonKind, palette: SkinPalette, fill: Color, enabledFill: Color = fill): ButtonLook = when (style) {
    CardStyle.MATERIAL -> ButtonLook.MATERIAL
    CardStyle.STRIPE -> if (kind == ButtonKind.FILLED) ButtonLook(flat = true) else ButtonLook.MATERIAL
    CardStyle.FLAT -> ButtonLook(flat = true)
    CardStyle.BRACKETS -> bracketsButton(kind, palette)
    CardStyle.GRAPHITE -> graphiteButton(kind, palette, fill)
    CardStyle.OUTLINED, CardStyle.BORDER_ONLY -> ButtonLook(
        containerAlpha = if (kind == ButtonKind.OUTLINED) 0f else 1f,
        flat = style == CardStyle.BORDER_ONLY,
    )
    CardStyle.GLASS -> glassButton(kind, palette, fill, enabledFill)
}

const val BUTTON_GLOW_ALPHA = 0.35f
val BUTTON_GLOW_SPREAD: Dp = 12.dp
const val CHIP_GLOW_ALPHA = 0.40f
val CHIP_GLOW_SPREAD: Dp = 14.dp

/**
 * The caller's [ButtonColors] in [look]. A pill or a gradient makes the M3 container see-through (the pill is
 * the fill); a [ButtonLook.containerAlpha] below 1 thins it; [isDefault] (the caller passed M3's
 * own colours) lets [ButtonLook.contentColor] in. MATERIAL gives the caller's instance itself.
 */
fun ButtonColors.withLook(look: ButtonLook, isDefault: Boolean): ButtonColors {
    var c = this
    if (look.underlay) {
        c = c.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent)
    } else if (look.containerAlpha != 1f) {
        c = c.copy(
            containerColor = containerColor.copy(alpha = containerColor.alpha * look.containerAlpha),
            disabledContainerColor = disabledContainerColor.copy(alpha = disabledContainerColor.alpha * look.containerAlpha),
        )
    }
    if (isDefault && look.contentColor.isSpecified) c = c.copy(contentColor = look.contentColor)
    return c
}

/**
 * A filter chip in one style, for its state (selected, enabled).
 * - [pillFill] and [pillEdge]: the Graphite home's pill under a see-through container ([pill]);
 * - [transparent] clears the container colours of every state;
 * - [label] is the label and icon colour of this state; Unspecified keeps the caller's;
 * - [border] ([borderColor], [borderFadeColor]) takes the place of a border the caller left
 *   unset; [noBorder] takes it away; [flat] drops the elevation, [raised] gives it 1 dp;
 * - [fill] is the container colour of this state (every state's container, when set);
 * - [glow] ([glowSpread] wide) is drawn behind the chip, under a frame as the pill's.
 */
data class ChipLook(
    val pillFill: Color = Color.Unspecified,
    val pillEdge: Color = Color.Unspecified,
    val transparent: Boolean = false,
    val label: Color = Color.Unspecified,
    val border: CardBorderKind = CardBorderKind.NONE,
    val borderColor: Color = Color.Unspecified,
    val borderFadeColor: Color = Color.Unspecified,
    val flat: Boolean = false,
    val fill: Color = Color.Unspecified,
    val noBorder: Boolean = false,
    val raised: Boolean = false,
    val glow: Color = Color.Unspecified,
    val glowSpread: Dp = 0.dp,
) {
    val pill: Boolean get() = pillFill.isSpecified

    /** The chip is drawn without M3's own touch-target padding around it, which the wrapper pads outside instead. */
    val framed: Boolean get() = pill || glowSpread > 0.dp

    /** The M3 chip with the caller's arguments, untouched. */
    val passThrough: Boolean get() = this == MATERIAL

    companion object {
        /** The MATERIAL look, one shared value: a Classic chip allocates none. */
        val MATERIAL = ChipLook()
    }
}

/**
 * The chip under the card [style] in [palette]; [gr] is the Graphite scheme, read by GRAPHITE only.
 * - GRAPHITE: the Graphite home's chips. Selected, the accent-tinted key (highlight at 16 % with
 *   a 50 % highlight edge, a full contour on the Edge scheme and a 1 px top highlight in the
 *   others, and a highlight label), the same look as the selected settings section; otherwise the
 *   top bar's plaque (chip with chipEdge) and textSecondary. Disabled, the pill at M3's disabled
 *   alpha;
 * - BRACKETS (Cockpit) and BORDER_ONLY (OLED): an outline only. No container; the edge and the
 *   selected label in the accent, the unselected edge in cardBorder;
 * - OUTLINED (Graphite Pro): a raised segment. Selected, a cardBorder fill, textPrimary and a 1 dp
 *   shadow; otherwise see-through, textSecondary; no border;
 * - GLASS (Volt): selected, a solid accent with a navyDark label and a glow; otherwise
 *   cardSurfaceElevated at 80 % with a 1 dp White 8 % edge and a textSecondary label;
 * - STRIPE (Telemetry): selected, a solid accent with a navyDark label; otherwise
 *   cardSurfaceElevated, textSecondary, no border;
 * - FLAT: no elevation.
 * A disabled chip dims its edge and fill.
 */
fun chipLook(style: CardStyle, selected: Boolean, enabled: Boolean, palette: SkinPalette, gr: GrPalette): ChipLook =
    when (style) {
        CardStyle.MATERIAL -> ChipLook.MATERIAL
        CardStyle.FLAT -> ChipLook(flat = true)
        CardStyle.OUTLINED -> raisedChip(selected, enabled, palette)
        CardStyle.STRIPE -> solidChip(selected, enabled, palette)
        CardStyle.GRAPHITE -> graphiteChip(selected, enabled, gr)
        CardStyle.BORDER_ONLY, CardStyle.BRACKETS -> outlineChip(selected, enabled, palette, flat = style == CardStyle.BORDER_ONLY)
        CardStyle.GLASS -> glassChip(selected, enabled, palette)
    }

internal fun Color.dimmed(): Color = copy(alpha = alpha * SWITCH_DISABLED_ALPHA)

/** A border that shows nothing: none at all, or a solid transparent one (`borderColor = Color.Transparent`). */
fun BorderStroke?.showsNothing(): Boolean = this == null || (brush as? SolidColor)?.value?.alpha == 0f

/**
 * The border a styled button or chip draws. The caller's own border stays, unless the caller left
 * it unset: [default] (M3's, passed when the style needs it) or one that shows nothing. Then [drop]
 * (a Graphite pill draws its own rim) gives none, else the style's [styled] border. A style with
 * neither keeps the caller's border, whatever it is.
 */
fun styledBorder(caller: BorderStroke?, default: BorderStroke?, styled: BorderStroke?, drop: Boolean): BorderStroke? {
    if (!drop && styled == null) return caller
    val unset = caller == default || caller.showsNothing()
    return when {
        !unset -> caller
        drop -> null
        else -> styled
    }
}

/** A percent corner that makes a pill: M3's CornerFull, the buttons' default shape. */
private const val PILL_PERCENT = 50

/**
 * The shape of a button whose caller passed [shape], [default] being M3's for that button (the
 * full pill, CircleShape). The caller's own shape stays: the fork RoundedCornerShape already
 * follows the skin. M3's pill follows the skin's corner style, its percent kept (a CUT skin cuts
 * it), as RoundedCornerShape(50) does; a skin with square corners (scale 0) gets square buttons. The classic corners give the caller's shape itself.
 */
fun skinButtonShape(shape: Shape, default: Shape, corners: SkinCorners): Shape =
    when {
        corners == SkinCorners.CLASSIC || shape != default || default != CircleShape -> shape
        // square skin: M3's pill is square too (percentShapeFor keeps its percent, a round dot elsewhere stays round)
        corners.scale == 0f -> shapeFor(corners, 0.dp, 0.dp, 0.dp, 0.dp)
        else -> percentShapeFor(corners, PILL_PERCENT)
    }

/**
 * An outlined text field in one style: [fill] fills a see-through container (the Graphite home's
 * sunken block); [containerAlpha] multiplies the alpha of the caller's container colours.
 */
data class FieldLook(val fill: Color = Color.Unspecified, val containerAlpha: Float = 1f) {
    /** The M3 field with the caller's arguments, untouched. */
    val passThrough: Boolean get() = this == MATERIAL

    companion object {
        /** The MATERIAL look, one shared value: a Classic field allocates none. */
        val MATERIAL = FieldLook()
    }
}

/**
 * The field under the card [style]. M3's outlined field already is an outline over nothing, in
 * the caller's colours and the skin's corners, so most styles keep it:
 * - GRAPHITE: a sunken block (navyMid, the inset colour of the Graphite home) inside the outline;
 * - GLASS: a caller's container see-through as the glass card's; BORDER_ONLY: none at all.
 */
fun fieldLook(style: CardStyle, palette: SkinPalette): FieldLook = when (style) {
    CardStyle.MATERIAL, CardStyle.OUTLINED, CardStyle.FLAT, CardStyle.STRIPE, CardStyle.BRACKETS -> FieldLook.MATERIAL
    CardStyle.GRAPHITE -> FieldLook(fill = palette.navyMid)
    CardStyle.GLASS -> FieldLook(containerAlpha = GLASS_ALPHA)
    CardStyle.BORDER_ONLY -> FieldLook(containerAlpha = 0f)
}

/** The caller's container colour [caller] in [look]: a see-through one takes the look's fill, any other the look's alpha. */
fun fieldContainer(caller: Color, look: FieldLook): Color = when {
    look.fill.isSpecified && caller.alpha == 0f -> look.fill
    look.containerAlpha == 1f -> caller
    else -> caller.copy(alpha = caller.alpha * look.containerAlpha)
}

/**
 * A styled checkbox or radio button face in M3's 20 dp box. [on] fills the checked box, and
 * strokes the selected ring and fills its dot; [off] strokes the unchecked box and ring; [mark] is
 * the check mark over [on]. [alpha] is 0.38 when disabled, as M3 does.
 */
data class CheckLook(val on: Color, val off: Color, val mark: Color, val alpha: Float)

val CheckBoxSize: Dp = 20.dp
val CheckStroke: Dp = 2.dp
val CheckPadding: Dp = 2.dp
val RadioDotSize: Dp = 10.dp

/** M3's state layer around a check, 40 dp across. */
val CheckRippleRadius: Dp = 20.dp

/**
 * The face of [style]; null for MATERIAL, which M3 draws itself. SQUARE (Telemetry) takes its
 * colours from the skin palette, as the styled switches do: the accent on, textMuted off,
 * navyDark for the check mark.
 */
fun checkLook(style: CheckStyle, enabled: Boolean, palette: SkinPalette): CheckLook? = when (style) {
    CheckStyle.MATERIAL -> null
    CheckStyle.SQUARE -> CheckLook(
        on = palette.accentGreen,
        off = palette.textMuted,
        mark = palette.navyDark,
        alpha = if (enabled) 1f else SWITCH_DISABLED_ALPHA,
    )
}
