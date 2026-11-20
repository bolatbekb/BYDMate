package com.bydmate.app.ui.skin

/** Which font family a skin sets for the app's text. SYSTEM is the author's own (no fontFamily). */
enum class SkinFont { SYSTEM, INTER, EXO2, MANROPE, ROBOTO_CONDENSED, RAJDHANI }

enum class CornerStyle { ROUNDED, CUT }

/** GRAPHITE, here and in the card and background styles, is the Graphite home's own look (GraphiteSkin.kt). */
enum class SwitchStyle { MATERIAL, SQUARE, PILL, OUTLINE, GRAPHITE }
enum class CardStyle { MATERIAL, OUTLINED, FLAT, GLASS, STRIPE, BORDER_ONLY, GRAPHITE }
enum class SliderStyle { MATERIAL, THIN, SEGMENTED }
enum class BackgroundStyle { AUTHOR, GRID, GLOW, SCANLINES, GRAPHITE }

/**
 * The bottom bar (SkinNavigationBar.kt). MATERIAL is the author's M3 bar; GRAPHITE is the Graphite
 * home's menu (a card dock, one-row keys, the raised lit key); CUT, UNDERLINE and OUTLINE change
 * only the selected key's indicator: a cut plate, an underline, an outline.
 */
enum class NavStyle { MATERIAL, GRAPHITE, CUT, UNDERLINE, OUTLINE }

/** The checkbox and the radio button (SkinChecks.kt): MATERIAL is M3's; SQUARE draws a square checkbox; its radio button stays round so it never reads as a checkbox. */
enum class CheckStyle { MATERIAL, SQUARE }

/**
 * [sizeFactor] evens out fonts that run wider or narrower than Roboto; it multiplies the user's text scale.
 * [numeric] is the font of the author's monospace numbers (SkinNumbers.kt); null keeps the monospace.
 */
data class SkinFonts(val text: SkinFont = SkinFont.SYSTEM, val sizeFactor: Float = 1f, val numeric: SkinFont? = null)

data class SkinCorners(val style: CornerStyle = CornerStyle.ROUNDED, val scale: Float = 1f) {
    companion object {
        /** The classic corners (rounded, unscaled), one shared value to compare with. */
        val CLASSIC = SkinCorners()
    }
}

data class SkinControls(
    val switch: SwitchStyle = SwitchStyle.MATERIAL,
    val card: CardStyle = CardStyle.MATERIAL,
    val slider: SliderStyle = SliderStyle.MATERIAL,
    val nav: NavStyle = NavStyle.MATERIAL,
    val check: CheckStyle = CheckStyle.MATERIAL,
)

/** What a skin changes besides colour. The default is the author's look (Classic). */
data class SkinKit(
    val fonts: SkinFonts = SkinFonts(),
    val corners: SkinCorners = SkinCorners(),
    val controls: SkinControls = SkinControls(),
    val background: BackgroundStyle = BackgroundStyle.AUTHOR,
)
