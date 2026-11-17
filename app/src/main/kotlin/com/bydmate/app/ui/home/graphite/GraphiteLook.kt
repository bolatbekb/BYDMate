package com.bydmate.app.ui.home.graphite

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/*
 * The Graphite home's own look, chosen in Settings › Приложение › «Главный экран» › Graphite:
 * the colour scheme, the card material, the relief and the 2.5D edges. The rest of the app keeps
 * the skin from «Оформление».
 */

/** A colour scheme of the Graphite home; [id] is stored, [title] shown as is. */
enum class GrScheme(val id: String, val title: String, val palette: GrPalette) {
    SOFT("soft", "Soft", GrSoft),
    DARK("dark", "Dark", GrDark),
    EDGE("edge", "Edge", GrEdge),
    MOCHA("mocha", "Mocha", GrMocha),
    HUB("hub", "Hub", GrHub),
    ONE("one", "One", GrOne),
    CODE("code", "Code", GrCode),
    DILINK("dilink", "DiLink", GrDiLink),
    ;

    companion object {
        fun fromId(id: String?): GrScheme = entries.firstOrNull { it.id == id } ?: DARK
    }
}

/** The surface of every card: a texture over the fill (glass: a glare over the content). [id] is stored. */
enum class GrMaterial(val id: String) {
    NONE("none"),
    GLASS("glass"),
    METAL("metal"),
    SAND_FINE("sand_fine"),
    CONCRETE("concrete"),
    ;

    /** The name shown in Settings, in [lang]. */
    fun title(lang: GrLang): String = lang.words.materials[ordinal]

    /** The Russian name. */
    val title: String get() = title(GrLang.RU)

    companion object {
        /** The removed coarse sand reads as fine sand. */
        fun fromId(id: String?): GrMaterial =
            if (id == "sand_coarse") SAND_FINE else entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** How the cards are shaped by light. [id] is stored. */
enum class GrRelief(val id: String) {
    FLAT("flat"),
    CONVEX("convex"),
    CONCAVE("concave"),
    ;

    /** The name shown in Settings, in [lang]. */
    fun title(lang: GrLang): String = lang.words.reliefs[ordinal]

    /** The Russian name. */
    val title: String get() = title(GrLang.RU)

    companion object {
        fun fromId(id: String?): GrRelief = entries.firstOrNull { it.id == id } ?: FLAT
    }
}

/** The Graphite home's type: Inter everywhere, or DiLink — Roboto text with Rajdhani numerals. [id] is stored. */
enum class GrFont(val id: String, val title: String) {
    INTER("inter", "Inter"),
    DILINK("dilink", "DiLink"),
    ;

    companion object {
        fun fromId(id: String?): GrFont = entries.firstOrNull { it.id == id } ?: INTER
    }
}

/** Material, relief and 2.5D edges of the cards; the default is the plain look. */
data class GrLook(
    val material: GrMaterial = GrMaterial.NONE,
    val relief: GrRelief = GrRelief.FLAT,
    val edges25d: Boolean = false,
)

/** The card look of the Graphite home; GraphiteHome provides it. */
val LocalGrLook = staticCompositionLocalOf { GrLook() }

/** The scheme for a saved app skin from 4.4.1.7–4.4.1.8, when the skin was one of the Graphite ones. */
fun schemeFromLegacySkin(skinId: String?): GrScheme? = when (skinId) {
    "graphite_soft" -> GrScheme.SOFT
    "graphite_dark" -> GrScheme.DARK
    "graphite_edge" -> GrScheme.EDGE
    "graphite_mocha" -> GrScheme.MOCHA
    "graphite_hub" -> GrScheme.HUB
    "graphite_one" -> GrScheme.ONE
    "graphite_code" -> GrScheme.CODE
    else -> null
}

/**
 * The saved Graphite look. Snapshot state, so the home redraws live when a setting changes.
 * Stored in `bydmate_home` next to the home style.
 */
object GraphiteLookState {
    private const val PREFS = "bydmate_home"
    private const val KEY_SCHEME = "graphite_scheme"
    private const val KEY_MATERIAL = "graphite_material"
    private const val KEY_RELIEF = "graphite_relief"
    private const val KEY_EDGES = "graphite_edges25d"
    private const val KEY_FONT = "graphite_font"

    // The app skin's file (SkinState): read once here to move a Graphite skin over.
    private const val SKIN_PREFS = "bydmate_skin"
    private const val SKIN_KEY = "skin_id"
    private const val CLASSIC_SKIN = "classic"

    var scheme: GrScheme by mutableStateOf(GrScheme.DARK)
        private set
    var material: GrMaterial by mutableStateOf(GrMaterial.NONE)
        private set
    var relief: GrRelief by mutableStateOf(GrRelief.FLAT)
        private set
    var edges25d: Boolean by mutableStateOf(false)
        private set
    var font: GrFont by mutableStateOf(GrFont.INTER)
        private set

    val look: GrLook get() = GrLook(material, relief, edges25d)

    /**
     * Loads the saved look. A Graphite app skin saved by an older version becomes the Graphite
     * scheme, and the app skin is rewritten to Classic (SkinState already shows Classic for it).
     */
    fun init(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val skins = context.getSharedPreferences(SKIN_PREFS, Context.MODE_PRIVATE)
            schemeFromLegacySkin(skins.getString(SKIN_KEY, null))?.let { legacy ->
                prefs.edit().putString(KEY_SCHEME, legacy.id).apply()
                skins.edit().putString(SKIN_KEY, CLASSIC_SKIN).apply()
            }
            scheme = GrScheme.fromId(prefs.getString(KEY_SCHEME, null))
            material = GrMaterial.fromId(prefs.getString(KEY_MATERIAL, null))
            relief = GrRelief.fromId(prefs.getString(KEY_RELIEF, null))
            edges25d = prefs.getBoolean(KEY_EDGES, false)
            font = GrFont.fromId(prefs.getString(KEY_FONT, null))
        }
    }

    fun selectScheme(context: Context, value: GrScheme) {
        scheme = value
        prefs(context).edit().putString(KEY_SCHEME, value.id).apply()
    }

    fun selectMaterial(context: Context, value: GrMaterial) {
        material = value
        prefs(context).edit().putString(KEY_MATERIAL, value.id).apply()
    }

    fun selectRelief(context: Context, value: GrRelief) {
        relief = value
        prefs(context).edit().putString(KEY_RELIEF, value.id).apply()
    }

    fun selectEdges25d(context: Context, on: Boolean) {
        edges25d = on
        prefs(context).edit().putBoolean(KEY_EDGES, on).apply()
    }

    fun selectFont(context: Context, value: GrFont) {
        font = value
        prefs(context).edit().putString(KEY_FONT, value.id).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
