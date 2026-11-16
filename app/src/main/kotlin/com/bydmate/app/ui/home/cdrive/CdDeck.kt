@file:Suppress("MagicNumber") // overlays placed on the backplate's pixel grid (tools/cdrive/LAYOUT.md)

package com.bydmate.app.ui.home.cdrive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.bydmate.app.R
import com.bydmate.app.ui.home.MusicControls
import com.bydmate.app.ui.home.NowPlaying
import com.bydmate.app.ui.home.common.K
import com.bydmate.app.ui.home.common.PressArea
import com.bydmate.app.ui.home.common.Txt
import com.bydmate.app.ui.home.common.at
import com.bydmate.app.ui.home.labelLine
import java.util.Locale
import kotlin.math.roundToInt

private val TitleCyan = Color(0xFF8CFFFF)
private val TimeCyan = Color(0xFF8BFFFF)
private val StatusCyan = Color(0xFFB6FFFF)
private val SegLit = Color(0xFF4AF8EE)
private val SegLitEnd = Color(0xFF54D2C1)
private val SegLitRim = Color(0xFF6BFFFF)
private val SegUnlit = Color(0xFF121918)
private val SegUnlitRim = Color(0xFF30817A)

/** Title faces, largest first: shrink a little for a long line, then end it with an ellipsis. */
private val TitleSizes = listOf(38f, 35f, 32f)

/** The title line runs from the ink's left edge to just inside the display's right edge (1063). */
private const val TITLE_X = 485
private const val TITLE_W = 565

/**
 * The music display and the transport keys: "Artist – Title", the play position in seven-segment
 * digits, ▶ PLAYING / ❚❚ PAUSED and the 20-segment progress bar, on the printed display; the
 * printed keys send [controls]' commands, and the ▶ key is lit while music plays.
 */
@Composable
internal fun CdDeck(music: NowPlaying, controls: MusicControls) {
    val title = music.labelLine().replace(" — ", " – ")
    Txt(title, TITLE_X, 236, rememberFitSize(title, TITLE_W, TitleSizes), TitleCyan, glow = true, width = TITLE_W)
    SevenSegClock(clockText(music.positionMs), Modifier.at(495, 269, 177, 61))
    if (music.hasTrack) PlayStatus(music.isPlaying)
    val known = music.hasTrack && music.positionMs != null && music.durationMs != null
    ProgressSegments(if (known) music.progress else 0f)
    TransportKeys(music.isPlaying, controls)
}

/** The longest time the four digits can show, seconds (99:59). */
private const val CLOCK_MAX_S = 99 * 60 + 59L

/** "03:24" from the play position, "--:--" when unknown; stops at 99:59. */
internal fun clockText(positionMs: Long?): String {
    val ms = positionMs?.takeIf { it >= 0 } ?: return "--:--"
    val s = (ms / 1000).coerceAtMost(CLOCK_MAX_S).toInt()
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

/* ---------------------------------------------------------------- status */

@Composable
private fun PlayStatus(playing: Boolean) {
    Canvas(Modifier.at(740, 291, 18, 20)) {
        if (playing) {
            val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }
            drawPath(p, StatusCyan.copy(alpha = 0.35f), style = Stroke(3f * K * density))
            drawPath(p, StatusCyan)
        } else {
            val bar = size.width * 0.32f
            for (x in floatArrayOf(size.width * 0.06f, size.width * 0.62f)) {
                drawRect(StatusCyan.copy(alpha = 0.35f), Offset(x, 0f), Size(bar, size.height), style = Stroke(3f * K * density))
                drawRect(StatusCyan, Offset(x, 0f), Size(bar, size.height))
            }
        }
    }
    // the display is flat to x 1063: a wide box, so no face's measure can ellipsize the word
    Txt(if (playing) "PLAYING" else "PAUSED", 772, 301, 29.5f, StatusCyan, glow = true, width = 260)
}

/* ---------------------------------------------------------------- seven-segment clock */

/** Digit box in backplate pixels (upright), stroke thickness, slant (x shift per px of height). */
private const val DIGIT_W = 23f
private const val DIGIT_H = 40f
private const val SEG_T = 5.5f
private const val SEG_GAP = 1.2f
private const val SLANT = 0.2f

/** Where each of the five characters' boxes start, left at the baseline, relative to the canvas. */
private val CharX = floatArrayOf(12f, 46f, 81f, 96f, 130f)
private const val CLOCK_TOP = 9f // 278 in the backplate

/** Segments a…g of each digit (bit 0 = a … bit 6 = g); '-' lights g only. */
private val DigitMask = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)
private const val DASH_MASK = 0x40

/** The big play-position counter, slanted seven-segment digits like the mockup's "03:24". */
@Composable
private fun SevenSegClock(text: String, modifier: Modifier) {
    Canvas(modifier) {
        val s = K * density
        val path = Path()
        text.forEachIndexed { i, ch ->
            val x0 = CharX.getOrElse(i) { 0f }
            when {
                ch == ':' -> colon(path, x0, s)
                ch == '-' -> segments(path, x0, DASH_MASK, s)
                ch.isDigit() -> segments(path, x0, DigitMask[ch - '0'], s)
            }
        }
        drawPath(path, TimeCyan.copy(alpha = 0.3f), style = Stroke(3.5f * s))
        drawPath(path, Color.Black.copy(alpha = 0.6f), style = Stroke(1.2f * s))
        drawPath(path, TimeCyan)
    }
}

/** A point of a character box at (x, y) from its top-left, slanted and scaled to the canvas. */
private fun pt(x0: Float, x: Float, y: Float, s: Float): Offset =
    Offset((x0 + x + (DIGIT_H - y) * SLANT) * s, (CLOCK_TOP + y) * s)

private fun segments(path: Path, x0: Float, mask: Int, s: Float) {
    val h = SEG_T / 2
    val l = h
    val r = DIGIT_W - h
    val top = h
    val mid = DIGIT_H / 2
    val bot = DIGIT_H - h
    // a, b, c, d, e, f, g as (from, to) on the centre lines between the corners
    val segs = arrayOf(
        floatArrayOf(l, top, r, top), floatArrayOf(r, top, r, mid), floatArrayOf(r, mid, r, bot),
        floatArrayOf(l, bot, r, bot), floatArrayOf(l, mid, l, bot), floatArrayOf(l, top, l, mid),
        floatArrayOf(l, mid, r, mid),
    )
    for (k in 0 until 7) {
        if (mask and (1 shl k) == 0) continue
        hexSegment(path, x0, segs[k], s)
    }
}

/** A pointed bar from (ax, ay) to (bx, by) — [seg] — along x or y, shortened by [SEG_GAP] at both ends. */
private fun hexSegment(path: Path, x0: Float, seg: FloatArray, s: Float) {
    val ax = seg[0]
    val ay = seg[1]
    val bx = seg[2]
    val by = seg[3]
    val h = SEG_T / 2
    val g = SEG_GAP
    val pts = if (ay == by) {
        val a = ax + g
        val b = bx - g
        listOf(a to ay, a + h to ay - h, b - h to ay - h, b to ay, b - h to ay + h, a + h to ay + h)
    } else {
        val a = ay + g
        val b = by - g
        listOf(ax to a, ax + h to a + h, ax + h to b - h, ax to b, ax - h to b - h, ax - h to a + h)
    }
    pts.forEachIndexed { i, (x, y) ->
        val p = pt(x0, x, y, s)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
}

private fun colon(path: Path, x0: Float, s: Float) {
    val h = SEG_T / 2
    for (y in floatArrayOf(DIGIT_H * 0.25f, DIGIT_H * 0.78f)) {
        // a square dot slanted like the digits
        val pts = listOf(pt(x0, -h, y - h, s), pt(x0, h, y - h, s), pt(x0, h, y + h, s), pt(x0, -h, y + h, s))
        path.moveTo(pts[0].x, pts[0].y)
        for (k in 1 until pts.size) path.lineTo(pts[k].x, pts[k].y)
        path.close()
    }
}

/* ---------------------------------------------------------------- progress */

private const val SEGMENTS = 20
private const val SEG_PITCH = 34.05f
private const val SEG_W = 32f

/** The mockup's 20-segment bar: lit cyan blocks, the last few fading, the rest dark with a teal rim. */
@Composable
private fun ProgressSegments(progress: Float) {
    Canvas(Modifier.at(340, 350, 690, 26)) {
        val s = K * density
        val lit = (progress.coerceIn(0f, 1f) * SEGMENTS).roundToInt()
        val y = 4f * s
        val h = 18f * s
        val w = SEG_W * s
        for (i in 0 until SEGMENTS) {
            val x = (4f + SEG_PITCH * i) * s
            if (i < lit) {
                val fade = ((i - (lit - 4)).coerceAtLeast(0) / 3f)
                val fill = lerp(SegLit, SegLitEnd, fade)
                drawRect(fill.copy(alpha = 0.25f), Offset(x - 2f * s, y - 2f * s), Size(w + 4f * s, h + 4f * s))
                drawRect(fill, Offset(x, y), Size(w, h))
                drawRect(SegLitRim.copy(alpha = 0.6f), Offset(x, y), Size(w, h), style = Stroke(1.5f * s))
            } else {
                drawRect(SegUnlit, Offset(x, y), Size(w, h))
                drawRect(SegUnlitRim, Offset(x, y), Size(w, h), style = Stroke(2f * s))
            }
        }
    }
}

/* ---------------------------------------------------------------- transport keys */

private class TransportKey(val x: Int, val w: Int, val name: String)

private const val TRANSPORT_Y = 425
private const val TRANSPORT_H = 59

private val TransportKeys = listOf(
    TransportKey(301, 127, "Назад"), TransportKey(433, 126, "Стоп"), TransportKey(564, 127, "Пауза"),
    TransportKey(696, 125, "Играть"), TransportKey(826, 126, "Вперёд"), TransportKey(983, 86, "Извлечь"),
)

/**
 * The printed keys ⏪ ⏹ ⏸ ▶ ⏩ ⏏. ⏏ has nothing to open (no app-level "open the player" action
 * exists), so it only answers the touch.
 */
@Composable
private fun TransportKeys(playing: Boolean, controls: MusicControls) {
    if (playing) {
        Image(painterResource(R.drawable.cd_play_lit), null, Modifier.at(693, 423, 131, 66), contentScale = ContentScale.FillBounds)
    }
    val actions: List<() -> Unit> = listOf(
        controls::previous, controls::stop, controls::pause, controls::play, controls::next, {},
    )
    TransportKeys.forEachIndexed { i, k ->
        val eject = i == TransportKeys.lastIndex
        PressArea(Modifier.at(k.x, TRANSPORT_Y, k.w, TRANSPORT_H), k.name, actions[i], role = if (eject) null else Role.Button)
    }
}
