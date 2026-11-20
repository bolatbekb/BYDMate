package com.bydmate.app.ui.skin

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import com.bydmate.app.ui.home.common.K
import com.bydmate.app.ui.home.graphite.GR_CARD_RADIUS
import com.bydmate.app.ui.home.graphite.GrFont
import com.bydmate.app.ui.home.graphite.GrPalette
import com.bydmate.app.ui.home.graphite.GrScheme
import com.bydmate.app.ui.home.graphite.highlight
import com.bydmate.app.ui.theme.Skin
import com.bydmate.app.ui.theme.SkinPalette

// fork: the «Graphite» app skin. It has no look of its own: it takes the one the owner set for the
// Graphite home (GraphiteLookState: scheme, card material, relief, 2.5D edges, font). This file is
// the pure part, the palette and the kit; GraphiteSkinParts.kt draws the card, the background and
// the switch with the Graphite home's own code.

/** The id of the «Graphite» skin (stored) and its title, a name shown as is in every language. */
const val GRAPHITE_SKIN_ID = "graphite_home"
const val GRAPHITE_SKIN_TITLE = "Graphite"

/** The corner of an author card (`RoundedCornerShape(12.dp)`, also M3 `shapes.medium`), in dp. */
private const val AUTHOR_CARD_CORNER_DP = 12f

/**
 * Corner scale of the «Graphite» skin: an author 12 dp card corner becomes the Graphite card's
 * 34 mockup px = 17 dp, so the scale is 17 / 12 ≈ 1.42 (16 dp → 22.7 dp, a 28 dp dialog → 39.7 dp).
 */
internal const val GRAPHITE_CORNER_SCALE: Float = GR_CARD_RADIUS * K / AUTHOR_CARD_CORNER_DP

/** Inter runs a little wider than Roboto; the other Inter skins (Graphite Pro, OLED) use the same factor. */
private const val INTER_SIZE_FACTOR = 0.97f

/** accentTeal is halfway between the electric green and the cold blue. */
private const val TEAL_MIX = 0.5f

/**
 * The app palette of a Graphite scheme. Every token the author's screens read comes from a named
 * GrPalette field. The author fills with these tokens (screens, cards, chips, nav), so each one is
 * opaque: a see-through Graphite colour is laid over the surface it sits on in the home.
 *
 * | SkinPalette | GrPalette | why |
 * |---|---|---|
 * | navyDark | bg | the screen background |
 * | navyDeep | bgGlow | the lighter centre glow; the second stop of the author's gradient |
 * | navyMid | inset over cardTop | sunken blocks inside a card (volume, tabs, footer) |
 * | cardSurface | cardTop | the card fill, the top of its gradient |
 * | cardSurfaceElevated | tabActive over cardTop | the raised key: active tab, pressed menu key |
 * | cardBorder | track over cardTop | the empty part of scales; the author draws borders, dividers and slider tracks with it, and the Graphite cardEdge (2–20 %) would vanish there |
 * | accentGreen | highlight (accent, else electric) | the scheme's selection and progress colour; DiLink's is its blue #45A2FF |
 * | accentBlue | cold | |
 * | accentOrange | fuel | |
 * | accentTeal | electric and cold, mixed half and half | Graphite has no teal of its own |
 * | textPrimary | textPrimary | |
 * | textSecondary | textSecondary | |
 * | textMuted | textTertiary | |
 * | chartGrid | divider over cardTop | dividers and the chart grid |
 * | navBarBackground | bg | |
 * | navIndicator | tabActive over bg | the active menu key |
 * | socGreen, socYellow, socRed | not taken | SkinPalette's defaults; the SOC gauge's normal level reads accentGreen (SocGreen in ui/theme/Color.kt) and so follows highlight; socGreen stays for ConsumptionGood |
 */
fun grToSkinPalette(p: GrPalette): SkinPalette = SkinPalette(
    navyDark = p.bg,
    navyDeep = p.bgGlow,
    navyMid = p.inset.compositeOver(p.cardTop),
    cardSurface = p.cardTop,
    cardSurfaceElevated = p.tabActive.compositeOver(p.cardTop),
    cardBorder = p.track.compositeOver(p.cardTop),
    accentGreen = p.highlight,
    accentBlue = p.cold,
    accentOrange = p.fuel,
    accentTeal = lerp(p.electric, p.cold, TEAL_MIX),
    textPrimary = p.textPrimary,
    textSecondary = p.textSecondary,
    textMuted = p.textTertiary,
    chartGrid = p.divider.compositeOver(p.cardTop),
    navBarBackground = p.bg,
    navIndicator = p.tabActive.compositeOver(p.bg),
)

/**
 * The kit for the Graphite home's [font]: Inter, or DiLink (Roboto text, i.e. the system font;
 * its Rajdhani numerals stay on the home). Corners ×[GRAPHITE_CORNER_SCALE], the Graphite card,
 * switch, background and bottom menu, the thin slider.
 */
fun graphiteKit(font: GrFont): SkinKit = SkinKit(
    fonts = when (font) {
        GrFont.INTER -> SkinFonts(SkinFont.INTER, INTER_SIZE_FACTOR, numeric = SkinFont.INTER)
        GrFont.DILINK -> SkinFonts(SkinFont.SYSTEM, 1f, numeric = SkinFont.RAJDHANI)
    },
    corners = SkinCorners(CornerStyle.ROUNDED, GRAPHITE_CORNER_SCALE),
    controls = SkinControls(
        switch = SwitchStyle.GRAPHITE,
        card = CardStyle.GRAPHITE,
        slider = SliderStyle.THIN,
        nav = NavStyle.GRAPHITE,
    ),
    background = BackgroundStyle.GRAPHITE,
)

/**
 * The palettes and skins of every scheme and font, built once. Every colour token reads the
 * palette (Color.kt), many times per frame of a recomposition: a table read, no allocation.
 */
private object GraphiteSkins {
    val palettes: Array<SkinPalette> = Array(GrScheme.entries.size) { grToSkinPalette(GrScheme.entries[it].palette) }
    private val kits: Array<SkinKit> = Array(GrFont.entries.size) { graphiteKit(GrFont.entries[it]) }
    val skins: Array<Skin> = Array(palettes.size * kits.size) {
        Skin(GRAPHITE_SKIN_ID, GRAPHITE_SKIN_TITLE, palettes[it / kits.size], kits[it % kits.size])
    }
}

/** The app palette of [scheme]: always the same instance. */
internal fun graphitePalette(scheme: GrScheme): SkinPalette = GraphiteSkins.palettes[scheme.ordinal]

/** The «Graphite» skin in [scheme] and [font]: always the same instance for the same pair. */
internal fun graphiteSkin(scheme: GrScheme, font: GrFont): Skin =
    GraphiteSkins.skins[scheme.ordinal * GrFont.entries.size + font.ordinal]

/** Two shape sizes: a corner that does not depend on the size (dp, px) gives the same at both. */
private val ProbeSmall = Size(100f, 100f)
private val ProbeLarge = Size(1000f, 1000f)

/**
 * The corner of [shape] in mockup px, the unit of grCard's and grPill's radius (1 mockup px = [K]
 * dp). A dp or px corner keeps its size; a rectangle is square; a percent corner, which depends on
 * the size, takes [sizeDependent]: the Graphite card's own [GR_CARD_RADIUS] by default.
 */
internal fun grRadiusOf(shape: Shape, density: Density, sizeDependent: Float = GR_CARD_RADIUS): Float {
    if (shape === RectangleShape) return 0f
    val corner = (shape as? CornerBasedShape)?.topStart ?: return sizeDependent
    val small = corner.toPx(ProbeSmall, density)
    if (small != corner.toPx(ProbeLarge, density)) return sizeDependent
    return small / density.density / K
}

/**
 * The grPill radius of a percent corner, such as the M3 button's default pill: far more than any
 * button is tall. The draw clamps a corner to half the side, so the pill stays a pill.
 */
internal const val GR_PILL_RADIUS = 10_000f

/** The nested blocks' edge: the key edge at twice its alpha (capped at 1), so it reads inside a card. */
internal fun graphiteNestedEdge(edge: Color): Color = edge.copy(alpha = (edge.alpha * 2f).coerceAtMost(1f))

/** True when [edge] is opaque (Hub, Code): the nested edge keeps the key's own hairline, px(1), not 1 dp. */
internal fun graphiteNestedEdgeIsHairline(edge: Color): Boolean = edge.alpha >= 1f
