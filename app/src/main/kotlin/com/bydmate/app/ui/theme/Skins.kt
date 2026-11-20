package com.bydmate.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.bydmate.app.ui.home.graphite.GrFont
import com.bydmate.app.ui.home.graphite.GrScheme
import com.bydmate.app.ui.home.graphite.GraphiteLookState
import com.bydmate.app.ui.skin.BackgroundStyle
import com.bydmate.app.ui.skin.CardStyle
import com.bydmate.app.ui.skin.CheckStyle
import com.bydmate.app.ui.skin.CornerStyle
import com.bydmate.app.ui.skin.SkinControls
import com.bydmate.app.ui.skin.SkinCorners
import com.bydmate.app.ui.skin.SkinFont
import com.bydmate.app.ui.skin.SkinFonts
import com.bydmate.app.ui.skin.SkinKit
import com.bydmate.app.ui.skin.GRAPHITE_SKIN_ID
import com.bydmate.app.ui.skin.NavStyle
import com.bydmate.app.ui.skin.SliderStyle
import com.bydmate.app.ui.skin.SwitchStyle
import com.bydmate.app.ui.skin.graphitePalette
import com.bydmate.app.ui.skin.graphiteSkin

/**
 * Every color token the app reads through Color.kt. A skin swaps the whole set at once;
 * screens keep using the old names (AccentGreen, CardSurface, ...) and recompose on change.
 *
 * fork-local: the author's screens are untouched, only Color.kt delegates here.
 */
data class SkinPalette(
    // Background gradient anchors
    val navyDark: Color,
    val navyDeep: Color,
    val navyMid: Color,
    // Surface / cards
    val cardSurface: Color,
    val cardSurfaceElevated: Color,
    val cardBorder: Color,
    // Accents: [accentGreen] is the app's primary accent, not "green" as such
    val accentGreen: Color,
    val accentBlue: Color,
    val accentOrange: Color,
    val accentTeal: Color,
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // Charts / navigation
    val chartGrid: Color,
    val navBarBackground: Color,
    val navIndicator: Color = lerp(navBarBackground, accentGreen, 0.22f),
    // Traffic-light semantics: the same meaning in every skin
    val socGreen: Color = Color(0xFF4ADE80),
    val socYellow: Color = Color(0xFFFBBF24),
    val socRed: Color = Color(0xFFEF4444),
)

/** A selectable skin: stable [id] for storage, [title] shown as is (proper names, not translated). */
data class Skin(val id: String, val title: String, val palette: SkinPalette, val kit: SkinKit = SkinKit()) {
    /**
     * The «Graphite» skin, in any scheme and font. Set once here: SkinState.palette asks it for
     * every colour token, so that read is a field, not a string compare.
     */
    val isGraphite: Boolean = id == GRAPHITE_SKIN_ID
}

object Skins {
    /** The author's original look. */
    val CLASSIC = Skin(
        "classic", "Classic",
        SkinPalette(
            navyDark = Color(0xFF0A1628),
            navyDeep = Color(0xFF0D1B2A),
            navyMid = Color(0xFF1B2838),
            cardSurface = Color(0xFF162032),
            cardSurfaceElevated = Color(0xFF1D2940),
            cardBorder = Color(0xFF2A3A52),
            accentGreen = Color(0xFF4ADE80),
            accentBlue = Color(0xFF60A5FA),
            accentOrange = Color(0xFFFB923C),
            accentTeal = Color(0xFF2DD4BF),
            textPrimary = Color(0xFFE2E8F0),
            textSecondary = Color(0xFF94A3B8),
            textMuted = Color(0xFF64748B),
            chartGrid = Color(0xFF1E3048),
            navBarBackground = Color(0xFF0F1923),
            navIndicator = Color(0xFF1B3A4B),
        )
    )

    /**
     * «Graphite»: the look the owner set for the Graphite home (scheme, card material, relief,
     * 2.5D edges, font), see ui/skin/GraphiteSkin.kt. This entry is the default look (Dark,
     * Inter); what shows is [live], which follows GraphiteLookState.
     */
    val GRAPHITE_HOME: Skin = graphiteSkin(GrScheme.DARK, GrFont.INTER)

    /** 1 · Cockpit: near-black instrument panel, cyan accent. */
    val COCKPIT = Skin(
        "cockpit", "Cockpit",
        SkinPalette(
            navyDark = Color(0xFF05070A),
            navyDeep = Color(0xFF05070A),
            navyMid = Color(0xFF0B0F14),
            cardSurface = Color(0xFF090D12),
            cardSurfaceElevated = Color(0xFF0F151C),
            cardBorder = Color(0xFF1E2630),
            accentGreen = Color(0xFF3DE8FF),
            accentBlue = Color(0xFF9AF3FF),
            accentOrange = Color(0xFFFFB020),
            accentTeal = Color(0xFF7CFF6B),
            textPrimary = Color(0xFFE6EDF3),
            textSecondary = Color(0xFF8B98A5),
            textMuted = Color(0xFF5F6C78),
            chartGrid = Color(0xFF141B23),
            navBarBackground = Color(0xFF05070A),
        ),
        SkinKit(SkinFonts(SkinFont.EXO2, 0.95f, SkinFont.EXO2, glow = true), SkinCorners(CornerStyle.ROUNDED, 0f), SkinControls(SwitchStyle.HUD, CardStyle.BRACKETS, SliderStyle.THIN, NavStyle.TOP, CheckStyle.SQUARE), BackgroundStyle.GRID),
    )

    /** 2 · Graphite Pro: neutral graphite, periwinkle accent. */
    val GRAPHITE = Skin(
        "graphite", "Graphite Pro",
        SkinPalette(
            navyDark = Color(0xFF0F1013),
            navyDeep = Color(0xFF0F1013),
            navyMid = Color(0xFF16171B),
            cardSurface = Color(0xFF141519),
            cardSurfaceElevated = Color(0xFF1A1C21),
            cardBorder = Color(0xFF24262C),
            accentGreen = Color(0xFF8B96FF),
            accentBlue = Color(0xFFA5B0FF),
            accentOrange = Color(0xFFF0A35E),
            accentTeal = Color(0xFF5EEAD4),
            textPrimary = Color(0xFFEDEEF0),
            textSecondary = Color(0xFF8E939C),
            textMuted = Color(0xFF6E737C),
            chartGrid = Color(0xFF202227),
            navBarBackground = Color(0xFF0F1013),
            navIndicator = Color(0xFF2A2C33),
        ),
        SkinKit(SkinFonts(SkinFont.INTER, 0.97f), SkinCorners(CornerStyle.ROUNDED, 1f), SkinControls(SwitchStyle.IOS, CardStyle.OUTLINED, SliderStyle.MATERIAL, NavStyle.DOT), BackgroundStyle.AUTHOR),
    )

    /** 3 · Volt: deep navy, electric green with fuel amber. */
    val VOLT = Skin(
        "volt", "Volt",
        SkinPalette(
            navyDark = Color(0xFF060A14),
            navyDeep = Color(0xFF0A0F1E),
            navyMid = Color(0xFF0B1020),
            cardSurface = Color(0xFF0C1324),
            cardSurfaceElevated = Color(0xFF13203A),
            cardBorder = Color(0xFF1B2440),
            accentGreen = Color(0xFF39F5A0),
            accentBlue = Color(0xFF4DA3FF),
            accentOrange = Color(0xFFFFB020),
            accentTeal = Color(0xFFB98CFF),
            textPrimary = Color(0xFFE8ECF6),
            textSecondary = Color(0xFF8C96AE),
            textMuted = Color(0xFF6F7A94),
            chartGrid = Color(0xFF141C33),
            navBarBackground = Color(0xFF0A0F1E),
        ),
        SkinKit(SkinFonts(SkinFont.MANROPE, 0.97f, glow = true), SkinCorners(CornerStyle.ROUNDED, 1.5f), SkinControls(SwitchStyle.GLOW, CardStyle.GLASS, SliderStyle.GLOW, NavStyle.DOCK), BackgroundStyle.NEON),
    )

    /** 4 · Telemetry: race-telemetry black, signal orange. */
    val TELEMETRY = Skin(
        "telemetry", "Telemetry",
        SkinPalette(
            navyDark = Color(0xFF0B0B0B),
            navyDeep = Color(0xFF0B0B0B),
            navyMid = Color(0xFF0E0E0E),
            cardSurface = Color(0xFF111111),
            cardSurfaceElevated = Color(0xFF151515),
            cardBorder = Color(0xFF2A2A2A),
            accentGreen = Color(0xFFFF5A1F),
            accentBlue = Color(0xFFE8FF3D),
            accentOrange = Color(0xFFFFB020),
            accentTeal = Color(0xFFFF3D5A),
            textPrimary = Color(0xFFF2F2F2),
            textSecondary = Color(0xFF9A9A9A),
            textMuted = Color(0xFF6E6E6E),
            chartGrid = Color(0xFF1C1C1C),
            navBarBackground = Color(0xFF0B0B0B),
        ),
        SkinKit(SkinFonts(SkinFont.ROBOTO_CONDENSED, 1f, SkinFont.RAJDHANI), SkinCorners(CornerStyle.ROUNDED, 0.5f), SkinControls(SwitchStyle.SQUARE, CardStyle.STRIPE, SliderStyle.GRADIENT, NavStyle.UNDERLINE, CheckStyle.SQUARE), BackgroundStyle.CARBON),
    )

    /** 5 · OLED: pure black, lime accent. */
    val OLED = Skin(
        "oled", "OLED",
        SkinPalette(
            navyDark = Color(0xFF000000),
            navyDeep = Color(0xFF000000),
            navyMid = Color(0xFF000000),
            cardSurface = Color(0xFF0A0A0A),
            cardSurfaceElevated = Color(0xFF141414),
            cardBorder = Color(0xFF262626),
            accentGreen = Color(0xFFC6FF3D),
            accentBlue = Color(0xFF5CE1FF),
            accentOrange = Color(0xFFFFB020),
            accentTeal = Color(0xFF5CE1FF),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFA3A3A3),
            textMuted = Color(0xFF5C5C5C),
            chartGrid = Color(0xFF1A1A1A),
            navBarBackground = Color(0xFF000000),
        ),
        SkinKit(SkinFonts(SkinFont.INTER, 0.97f), SkinCorners(CornerStyle.ROUNDED, 1f), SkinControls(SwitchStyle.OUTLINE, CardStyle.BORDER_ONLY, SliderStyle.MATERIAL, NavStyle.OUTLINE), BackgroundStyle.AUTHOR),
    )

    /**
     * «Цвета приложения»: the app-wide skins. The Graphite home has its own schemes
     * (GraphiteLookState); the «Graphite» skin, last (owner, 2026-11-19), takes whichever is set there.
     */
    val ALL = listOf(CLASSIC, COCKPIT, GRAPHITE, VOLT, TELEMETRY, OLED, GRAPHITE_HOME)

    fun byId(id: String?): Skin = ALL.firstOrNull { it.id == id } ?: CLASSIC
}

/**
 * This skin as it shows now. The «Graphite» skin takes the Graphite home's scheme and font, so
 * for it this reads GraphiteLookState (snapshot state): a change there recomposes the readers.
 * Every other skin is itself. No allocation: the Graphite skins are built once (graphiteSkin).
 */
fun Skin.live(): Skin = if (isGraphite) graphiteSkin(GraphiteLookState.scheme, GraphiteLookState.font) else this

/**
 * The skin in effect. Snapshot state: any composable that reads a color token
 * recomposes when the skin changes, so the switch is live, no restart needed.
 */
object SkinState {
    private const val PREFS = "bydmate_skin"
    private const val KEY = "skin_id"

    /** The skin chosen in settings, as listed in [Skins.ALL]. */
    var selected: Skin by mutableStateOf(Skins.CLASSIC)
        private set

    /** The skin in effect: [selected] in its [live] form, so «Graphite» follows the Graphite home. */
    val current: Skin get() = selected.live()

    /** The palette in effect; for «Graphite» the Graphite home's scheme (it reads the scheme only, not the font). */
    val palette: SkinPalette
        get() = selected.let { if (it.isGraphite) graphitePalette(GraphiteLookState.scheme) else it.palette }

    /** Loads the saved skin. Call once from Application.onCreate, before any UI. */
    fun init(context: Context) {
        val id = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.getOrNull()
        selected = Skins.byId(id)
    }

    fun select(context: Context, skin: Skin) {
        selected = skin
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, skin.id).apply()
    }
}
