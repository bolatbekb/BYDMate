package com.bydmate.app.ui.home

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.bydmate.app.R

/*
 * Photographed cassettes for the retro decks (credits: assets/licenses/cassettes.md, FORK.md).
 * Each face is 1004 x 638 px with alpha; the hub patches are circles cut from the same photo,
 * centred on [PhotoCassette.hubL] / [PhotoCassette.hubR] (pixel-edge coordinates of the face).
 */

/** One photographed cassette: the face, its two hub patches and where the handwritten label goes. */
data class PhotoCassette(
    @DrawableRes val face: Int,
    @DrawableRes val hubLImage: Int,
    @DrawableRes val hubRImage: Int,
    val hubL: Offset,
    val hubR: Offset,
    val label: Rect,
    val ink: Color,
)

/** Pixel size of every face. */
const val CASSETTE_W = 1004f
const val CASSETTE_H = 638f

val PhotoCassettes = listOf(
    PhotoCassette(
        R.drawable.cassette_tdk_sa60, R.drawable.cassette_tdk_sa60_hub_l, R.drawable.cassette_tdk_sa60_hub_r,
        Offset(292.5f, 286.5f), Offset(719.5f, 285.5f), Rect(130f, 386f, 130f + 773f, 386f + 66f), Color(0xFF2B2F6B),
    ),
    PhotoCassette(
        R.drawable.cassette_tdk_arx90, R.drawable.cassette_tdk_arx90_hub_l, R.drawable.cassette_tdk_arx90_hub_r,
        Offset(291f, 288f), Offset(716f, 288f), Rect(118f, 390f, 118f + 772f, 390f + 74f), Color(0xFF2B2F6B),
    ),
    PhotoCassette(
        R.drawable.cassette_tdk_d60, R.drawable.cassette_tdk_d60_hub_l, R.drawable.cassette_tdk_d60_hub_r,
        Offset(290f, 295f), Offset(715f, 295f), Rect(515f, 35f, 515f + 330f, 35f + 65f), Color(0xFF1E2A4A),
    ),
    PhotoCassette(
        R.drawable.cassette_tdk_d46, R.drawable.cassette_tdk_d46_hub_l, R.drawable.cassette_tdk_d46_hub_r,
        Offset(291f, 291f), Offset(723f, 292f), Rect(80f, 30f, 80f + 840f, 30f + 42f), Color(0xFFEDE6D3),
    ),
)

/** Which cassette is in the deck: the next one on every app start. */
object CassetteRotation {
    private const val PREFS = "bydmate_cassette"
    private const val KEY = "index"

    var index by mutableIntStateOf(0)
        private set

    val current: PhotoCassette get() = PhotoCassettes[index % PhotoCassettes.size]

    /** Takes this start's cassette and moves the counter on; once per process. */
    fun init(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val n = prefs.getInt(KEY, 0).coerceAtLeast(0)
            index = n % PhotoCassettes.size
            prefs.edit().putInt(KEY, (n + 1) % PhotoCassettes.size).apply()
        }
    }
}

/**
 * What is written on the cassette label: "artist — title", the title alone without an artist.
 */
fun NowPlaying.labelLine(): String {
    val t = title?.trim().orEmpty()
    val a = artist?.trim().orEmpty()
    return when {
        t.isEmpty() -> "Нет музыки"
        a.isEmpty() -> t
        else -> "$a — $t"
    }
}

/** The tape counter's text: the running time. */
fun NowPlaying.counterText(): String = positionMs.tapeTime()
