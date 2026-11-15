@file:Suppress("TooManyFunctions") // Canvas drawing of one hardware panel: many small private painters by design

package com.bydmate.app.ui.home.kenwood

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import com.bydmate.app.ui.home.brushedFascia
import com.bydmate.app.ui.home.hardwareKey
import com.bydmate.app.ui.home.spunCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Palette of the deck: black brushed aluminium and a white-blue fluorescent display. */
internal object Kw {
    val bg = Color(0xFF0A0A0B)
    val text = Color(0xFFC9CBCF)
    val edge = Color(0xFF45474C)
    val deep = Color(0xFF050506)
    val key = Color(0xFF141517)
    val keyLit = Color(0xFF1E2A38)
    val logo = Color(0xFFDADCE0)
    val grey = Color(0xFFA8ABB0)
    val vfdBg = Color(0xFF050607)
    val vfd = Color(0xFFDDEBFF)
    val vfdDim = Color(0xFF9DB4D6)
    val vfdOff = Color(0xFF2B3440)
    val vfdCellOff = Color(0xFF121820)
    val vfdRed = Color(0xFFFF4A3D)
    val led = Color(0xFFFF3B2F)
    val ampOff = Color(0xFF2A0F0D)
    val ampLabel = Color(0xFFFF8A80)
    val ampValue = Color(0xFFFF6A5E)
    val white = Color(0xFFE8EAED)
}

/** Stand-ins for the mockup's web fonts: Michroma / Exo 2 / JetBrains Mono italic. */
internal val LogoFont = FontFamily.SansSerif
internal val PanelFont = FontFamily.SansSerif
internal val VfdFont = FontFamily.Monospace

/** One line of panel lettering; sizes are design px (= dp = sp, the screen pins fontScale to 1). */
@Composable
internal fun KText(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    color: Color = Kw.text,
    family: FontFamily = PanelFont,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    spacing: Float = 0f,
    align: TextAlign? = null,
    glow: Color? = null,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            lineHeight = (size * 1.25f).sp,
            fontFamily = family,
            fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = spacing.sp,
            textAlign = align ?: TextAlign.Unspecified,
            shadow = glow?.let { Shadow(it, Offset.Zero, 14f) },
        ),
    )
}

/** Wordmark in the place of the maker's logo. */
@Composable
internal fun Logo(size: Float, modifier: Modifier = Modifier) =
    KText("BYDMATE", size, modifier, color = Kw.logo, family = LogoFont, weight = FontWeight.Medium, spacing = size * 0.09f)

/** A tier of the stack: horizontally brushed black aluminium, a lit top edge and a dark lip. */
internal fun Modifier.brushedTier(): Modifier = this
    .clip(RoundedCornerShape(3.dp))
    .brushedFascia(Color(0xFF28292C), Color(0xFF1B1C1E), Kw.edge, Kw.deep)

/** Black window with a grey frame: the display, analyzer and meter cut-outs. */
internal fun Modifier.window(bg: Color = Kw.vfdBg): Modifier = background(bg).border(1.dp, Kw.edge)

/** Face of a flat rectangular push key: black plate on a 2 dp shadow, or pushed in and lit blue. */
internal fun Modifier.keyFace(lit: Boolean, plate: Color = Color(0xFF18191B)): Modifier =
    hardwareKey(down = lit, face = if (lit) Kw.keyLit else plate, edge = Kw.edge, shadow = Kw.deep, radius = 2.dp)

/** Push key with a glyph; lights while held or while [on]. */
@Composable
internal fun DeckKey(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    glyphW: Dp = 20.dp,
    glyphH: Dp = 14.dp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lit = on || pressed
    Box(
        modifier
            .keyFace(lit)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, if (lit) Kw.vfd else Kw.text, glyphW, glyphH)
    }
}

/** Transport and status symbols, drawn on the mockup's 26 x 20 grid. */
internal enum class Glyph { STOP, PLAY, PAUSE, EJECT, PREV, NEXT }

private fun glyphPath(g: Glyph): Path = Path().apply {
    fun rect(l: Float, t: Float, r: Float, b: Float) {
        moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
    }
    fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) {
        moveTo(ax, ay); lineTo(bx, by); lineTo(cx, cy); close()
    }
    when (g) {
        Glyph.STOP -> rect(5f, 3f, 21f, 17f)
        Glyph.PLAY -> tri(7f, 2f, 7f, 18f, 21f, 10f)
        Glyph.PAUSE -> { rect(6f, 2f, 11f, 18f); rect(15f, 2f, 20f, 18f) }
        Glyph.EJECT -> { tri(13f, 2f, 23f, 12f, 3f, 12f); rect(3f, 15f, 23f, 18f) }
        Glyph.PREV -> { rect(2f, 2f, 5f, 18f); tri(24f, 2f, 24f, 18f, 8f, 10f) }
        Glyph.NEXT -> { tri(2f, 2f, 2f, 18f, 18f, 10f); rect(21f, 2f, 24f, 18f) }
    }
}

@Composable
internal fun GlyphIcon(glyph: Glyph, color: Color, w: Dp, h: Dp) {
    val path = remember(glyph) { glyphPath(glyph) }
    Canvas(Modifier.size(w, h)) {
        val s = min(size.width / 26f, size.height / 20f)
        withTransform({
            translate((size.width - 26f * s) / 2, (size.height - 20f * s) / 2)
            scale(s, s, Offset.Zero)
        }) { drawPath(path, color) }
    }
}

/** Domed panel screw. */
@Composable
internal fun Screw(modifier: Modifier = Modifier) {
    Canvas(modifier.size(10.dp)) {
        val c = Offset(size.width * 0.35f, size.height * 0.35f)
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF6A6D73), Color(0xFF26282C)), c, size.width * 0.92f),
        )
    }
}

/** Rocker-less POWER plate: caption, a dark square key, optional legend under it. */
@Composable
internal fun PowerPlate(caption: Float, w: Dp, h: Dp, legend: String?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        KText("POWER", caption, spacing = if (legend != null) 2f else 0f)
        Spacer(Modifier.height(if (legend != null) 4.dp else 3.dp))
        Box(
            Modifier
                .size(w, h)
                .background(Color(0xFF121315))
                .border(1.dp, Kw.edge)
                .drawBehind {
                    val lip = 3.dp.toPx()
                    drawRect(Kw.deep, Offset(1.dp.toPx(), size.height - lip - 1.dp.toPx()), Size(size.width - 2.dp.toPx(), lip))
                },
        )
        if (legend != null) {
            Spacer(Modifier.height(4.dp))
            KText(legend, 10f, color = Color(0xFF8E9196), spacing = 1f)
        }
    }
}

/** Small round knob with a caption above and a reading below (САЛОН, УЛИЦА...). */
@Composable
internal fun RoundButton(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        KText(label, 10f, spacing = 1f)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(Color(0xFF0E0F11))
                    spunCap(center, size.width * 0.42f)
                }
                .border(1.dp, Kw.edge, CircleShape),
        )
        Spacer(Modifier.height(3.dp))
        KText(value, 14f, color = Kw.white)
    }
}

/** Segmented fluorescent bar: [cells] cells, the last [redFrom].. lit red. Null lights nothing. */
@Composable
internal fun VfdCells(percent: Int?, modifier: Modifier = Modifier, cells: Int = 30, redFrom: Int = 24) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (cells - 1)) / cells
        val lit = percent?.let { (it.coerceIn(0, 100) / 100f * cells).roundToInt() } ?: 0
        val glow = 2.dp.toPx()
        for (i in 0 until lit) {
            val c = if (i >= redFrom) Kw.vfdRed else Kw.vfd
            drawRect(c.copy(alpha = 0.28f), Offset(i * (w + gap) - glow, -glow), Size(w + 2 * glow, size.height + 2 * glow))
        }
        for (i in 0 until cells) {
            val c = when {
                i >= lit -> Kw.vfdCellOff
                i >= redFrom -> Kw.vfdRed
                else -> Kw.vfd
            }
            drawRect(c, Offset(i * (w + gap), 0f), Size(w, size.height))
        }
    }
}

/** Red bar meter of the power amplifier: [km] on a 0..[maxKm] scale. */
@Composable
internal fun AmpCells(km: Int?, maxKm: Int, modifier: Modifier = Modifier, cells: Int = 40) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (cells - 1)) / cells
        val lit = km?.let { (it.coerceIn(0, maxKm).toFloat() / maxKm * cells).roundToInt() } ?: 0
        for (i in 0 until cells) {
            drawRect(if (i < lit) Kw.led else Kw.ampOff, Offset(i * (w + gap), 0f), Size(w, size.height))
        }
    }
}

/**
 * Graphic-equalizer slider: a black slot 16 dp wide with a 26 x 14 cap carrying a red LED.
 * [fraction] 0 = bottom, 1 = top; null parks the cap at the bottom.
 */
@Composable
internal fun EqSlider(fraction: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier.width(26.dp)) {
        val px = 1.dp.toPx()
        val slotL = 5 * px
        val slotW = 16 * px
        drawRect(Kw.deep, Offset(slotL, 0f), Size(slotW, size.height))
        drawRect(Color(0xFF2E3034), Offset(slotL + px / 2, px / 2), Size(slotW - px, size.height - px), style = Stroke(px))
        val capH = 14 * px
        val f = (fraction ?: 0f).coerceIn(0f, 1f)
        val top = (size.height - capH) * (1f - f)
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFF3A3C41), Color(0xFF18191C)), top, top + capH),
            Offset(0f, top), Size(size.width, capH),
        )
        drawRect(Kw.deep, Offset(px / 2, top + px / 2), Size(size.width - px, capH - px), style = Stroke(px))
        val led = Offset(size.width / 2, top + capH / 2)
        drawCircle(Brush.radialGradient(listOf(Kw.led.copy(alpha = 0.6f), Color.Transparent), led, 7 * px), 7 * px, led)
        drawCircle(Kw.led, 3 * px, led)
    }
}

/**
 * Knurled volume knob on a dotted 270-degree scale; the pip shows [value] of [max].
 * Null hides the pip rather than pointing at a made-up value.
 */
@Composable
internal fun KnurledKnob(value: Int?, max: Int, modifier: Modifier = Modifier, diameter: Dp = 150.dp) {
    Canvas(modifier.size(diameter)) {
        val u = size.width / 150f
        val c = center
        fun at(deg: Float, r: Float): Offset {
            val a = Math.toRadians(deg.toDouble())
            return Offset(75 * u + r * u * cos(a).toFloat(), 75 * u - r * u * sin(a).toFloat())
        }
        for (i in 0..10) drawCircle(Kw.text, 2 * u, at(225f - i * 27f, 70f))
        val shadowC = c + Offset(0f, 4 * u)
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.8f), Color.Transparent), shadowC, 63 * u),
            63 * u, shadowC,
        )
        drawKnurl(c, 53 * u)
        spunCap(c, 41 * u, Color(0xFF17181B), Color(0xFF3A3C41))
        if (value != null) drawCircle(Kw.white, 4 * u, at(225f - value.coerceIn(0, max).toFloat() / max * 270f, 30f))
    }
}

private fun DrawScope.drawKnurl(c: Offset, r: Float) {
    drawCircle(Color(0xFF15161A), r, c)
    val tl = c - Offset(r, r)
    val sz = Size(2 * r, 2 * r)
    for (j in 0 until 60) {
        drawArc(Color(0xFF24262A), j * 6f + 3f - 90f, 3f, useCenter = true, topLeft = tl, size = sz)
    }
}

/**
 * Seconds of running time, advanced each frame only while [running]; stops where it was so
 * reels and bars pick up without a jump. Read it inside a draw lambda to redraw only that layer.
 */
@Composable
internal fun rememberRunningClock(running: Boolean, speed: Float = 1f): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, speed) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                t.floatValue = (t.floatValue + (now - last) / 1e9f * speed) % 36_000f
                last = now
            }
        }
    }
    return t
}

/**
 * The mockup's `eq` keyframes (0% .3, 35% 1, 70% .55, 100% .85), ease-in-out, played
 * alternately: scale of one analyzer bar at time [t].
 */
internal fun eqScale(t: Float, delay: Float, dur: Float): Float {
    val p = (t + delay) / dur
    val cycle = floor(p).toInt()
    var u = p - cycle
    if (cycle % 2 == 1) u = 1f - u
    val keys = floatArrayOf(0f, 0.35f, 0.7f, 1f)
    val vals = floatArrayOf(0.3f, 1f, 0.55f, 0.85f)
    var i = 0
    while (i < 2 && u > keys[i + 1]) i++
    val local = ((u - keys[i]) / (keys[i + 1] - keys[i])).coerceIn(0f, 1f)
    val eased = local * local * (3 - 2 * local)
    return vals[i] + (vals[i + 1] - vals[i]) * eased
}

/** Fluorescent analyzer columns: 3 dp lit segments on a 5 dp pitch, rising from the floor. */
internal fun DrawScope.drawSegmentBar(x: Float, w: Float, height: Float, color: Color) {
    val seg = 3.dp.toPx()
    val pitch = 5.dp.toPx()
    val floorY = size.height
    val ceil = floorY - max(0f, height)
    var b = floorY
    while (b > ceil) {
        val top = max(b - seg, ceil)
        drawRect(color, Offset(x, top), Size(w, b - top))
        b -= pitch
    }
}
