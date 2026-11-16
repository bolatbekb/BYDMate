package com.bydmate.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.bydmate.app.ui.home.graphite.GrDark
import com.bydmate.app.ui.home.graphite.GrEdge
import com.bydmate.app.ui.home.graphite.GrCode
import com.bydmate.app.ui.home.graphite.GrHub
import com.bydmate.app.ui.home.graphite.GrMocha
import com.bydmate.app.ui.home.graphite.GrOne
import com.bydmate.app.ui.home.graphite.GrSoft
import com.bydmate.app.ui.home.graphite.toSkinPalette

/**
 * Every color token the app reads through Color.kt. A skin swaps the whole set at once;
 * screens keep using the old names (AccentGreen, CardSurface, ...) and recompose on change.
 *
 * Fork-local: the author's screens are untouched, only Color.kt delegates here.
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
data class Skin(val id: String, val title: String, val palette: SkinPalette)

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

    /** 1 · Cockpit: near-black instrument panel, cyan accent. */
    val COCKPIT = Skin(
        "cockpit", "Cockpit",
        SkinPalette(
            navyDark = Color(0xFF07090C),
            navyDeep = Color(0xFF090C10),
            navyMid = Color(0xFF0B0F14),
            cardSurface = Color(0xFF0B0F14),
            cardSurfaceElevated = Color(0xFF111820),
            cardBorder = Color(0xFF1E2630),
            accentGreen = Color(0xFF3DE8FF),
            accentBlue = Color(0xFF9AF3FF),
            accentOrange = Color(0xFFFFB020),
            accentTeal = Color(0xFF7CFF6B),
            textPrimary = Color(0xFFE6EDF3),
            textSecondary = Color(0xFF8B98A5),
            textMuted = Color(0xFF5F6C78),
            chartGrid = Color(0xFF1A222C),
            navBarBackground = Color(0xFF090C10),
        )
    )

    /** 2 · Graphite Pro: neutral graphite, periwinkle accent. */
    val GRAPHITE = Skin(
        "graphite", "Graphite Pro",
        SkinPalette(
            navyDark = Color(0xFF0E0F12),
            navyDeep = Color(0xFF0E0F12),
            navyMid = Color(0xFF16171B),
            cardSurface = Color(0xFF16171B),
            cardSurfaceElevated = Color(0xFF1A1C21),
            cardBorder = Color(0xFF26282E),
            accentGreen = Color(0xFF8B96FF),
            accentBlue = Color(0xFFA5B0FF),
            accentOrange = Color(0xFFF0A35E),
            accentTeal = Color(0xFF5EEAD4),
            textPrimary = Color(0xFFEDEEF0),
            textSecondary = Color(0xFF8A8F98),
            textMuted = Color(0xFF6E737C),
            chartGrid = Color(0xFF1E2025),
            navBarBackground = Color(0xFF0E0F12),
            navIndicator = Color(0xFF2A2C33),
        )
    )

    /** 3 · Volt: deep navy, electric green with fuel amber. */
    val VOLT = Skin(
        "volt", "Volt",
        SkinPalette(
            navyDark = Color(0xFF070B16),
            navyDeep = Color(0xFF0A0F1E),
            navyMid = Color(0xFF0B1020),
            cardSurface = Color(0xFF0E1426),
            cardSurfaceElevated = Color(0xFF131B32),
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
        )
    )

    /** 4 · Telemetry: race-telemetry black, signal orange. */
    val TELEMETRY = Skin(
        "telemetry", "Telemetry",
        SkinPalette(
            navyDark = Color(0xFF0A0A0A),
            navyDeep = Color(0xFF0A0A0A),
            navyMid = Color(0xFF0E0E0E),
            cardSurface = Color(0xFF0E0E0E),
            cardSurfaceElevated = Color(0xFF151515),
            cardBorder = Color(0xFF262626),
            accentGreen = Color(0xFFFF7A1A),
            accentBlue = Color(0xFFE8FF3D),
            accentOrange = Color(0xFFFFA866),
            accentTeal = Color(0xFFFF3D5A),
            textPrimary = Color(0xFFF2F2F2),
            textSecondary = Color(0xFF9A9A9A),
            textMuted = Color(0xFF6E6E6E),
            chartGrid = Color(0xFF1C1C1C),
            navBarBackground = Color(0xFF0A0A0A),
        )
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
        )
    )

    /** Graphite home skins: palettes live in ui.home.graphite. */
    val GRAPHITE_SOFT = Skin("graphite_soft", "Graphite Soft", GrSoft.toSkinPalette())
    val GRAPHITE_DARK = Skin("graphite_dark", "Graphite Dark", GrDark.toSkinPalette())
    val GRAPHITE_EDGE = Skin("graphite_edge", "Graphite Edge", GrEdge.toSkinPalette())
    val GRAPHITE_MOCHA = Skin("graphite_mocha", "Graphite Mocha", GrMocha.toSkinPalette())
    val GRAPHITE_HUB = Skin("graphite_hub", "Graphite Hub", GrHub.toSkinPalette())
    val GRAPHITE_ONE = Skin("graphite_one", "Graphite One", GrOne.toSkinPalette())
    val GRAPHITE_CODE = Skin("graphite_code", "Graphite Code", GrCode.toSkinPalette())

    val ALL = listOf(CLASSIC, COCKPIT, GRAPHITE, VOLT, TELEMETRY, OLED, GRAPHITE_SOFT, GRAPHITE_DARK, GRAPHITE_EDGE,
        GRAPHITE_MOCHA, GRAPHITE_HUB, GRAPHITE_ONE, GRAPHITE_CODE)

    fun byId(id: String?): Skin = ALL.firstOrNull { it.id == id } ?: CLASSIC
}

/**
 * The skin in effect. Snapshot state: any composable that reads a color token
 * recomposes when the skin changes, so the switch is live, no restart needed.
 */
object SkinState {
    private const val PREFS = "bydmate_skin"
    private const val KEY = "skin_id"

    var current: Skin by mutableStateOf(Skins.CLASSIC)
        private set

    val palette: SkinPalette get() = current.palette

    /** Loads the saved skin. Call once from Application.onCreate, before any UI. */
    fun init(context: Context) {
        val id = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }.getOrNull()
        current = Skins.byId(id)
    }

    fun select(context: Context, skin: Skin) {
        current = skin
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, skin.id).apply()
    }
}
