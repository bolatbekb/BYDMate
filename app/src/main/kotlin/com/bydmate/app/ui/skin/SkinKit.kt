package com.bydmate.app.ui.skin

/** Which font family a skin sets for the app's text. SYSTEM is the author's own (no fontFamily). */
enum class SkinFont { SYSTEM, INTER, EXO2, MANROPE, ROBOTO_CONDENSED }

enum class CornerStyle { ROUNDED, CUT }

/** GRAPHITE, here and in the card and background styles, is the Graphite home's own look (GraphiteSkin.kt). */
enum class SwitchStyle { MATERIAL, SQUARE, PILL, OUTLINE, GRAPHITE }
enum class CardStyle { MATERIAL, OUTLINED, FLAT, GLASS, STRIPE, BORDER_ONLY, GRAPHITE }
enum class SliderStyle { MATERIAL, THIN, SEGMENTED }
enum class BackgroundStyle { AUTHOR, GRID, GLOW, SCANLINES, GRAPHITE }

/** [sizeFactor] evens out fonts that run wider or narrower than Roboto; it multiplies the user's text scale. */
data class SkinFonts(val text: SkinFont = SkinFont.SYSTEM, val sizeFactor: Float = 1f)

data class SkinCorners(val style: CornerStyle = CornerStyle.ROUNDED, val scale: Float = 1f)

data class SkinControls(
    val switch: SwitchStyle = SwitchStyle.MATERIAL,
    val card: CardStyle = CardStyle.MATERIAL,
    val slider: SliderStyle = SliderStyle.MATERIAL,
)

/** What a skin changes besides colour. The default is the author's look (Classic). */
data class SkinKit(
    val fonts: SkinFonts = SkinFonts(),
    val corners: SkinCorners = SkinCorners(),
    val controls: SkinControls = SkinControls(),
    val background: BackgroundStyle = BackgroundStyle.AUTHOR,
)
