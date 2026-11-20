@file:Suppress("TooManyFunctions") // the shared kit of the mockup homes: small parts, one place

package com.bydmate.app.ui.home.common

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.min

/*
 * Shared by the mockup homes (C-Drive, Graphite): each is laid out on a 2560 x 1208 mockup, twice
 * the car's 1280 x 604 dp content area. Everything is placed on the mockup's pixel grid, converted
 * by [K].
 */

/** Mockup pixel to dp. */
internal const val K = 0.5f

internal fun px(v: Int): Dp = (v * K).dp
internal fun px(v: Float): Dp = (v * K).dp
internal fun fs(v: Float): TextUnit = (v * K).sp

/** Placed at mockup pixel ([x], [y]) with mockup size [w] x [h]. */
internal fun Modifier.at(x: Int, y: Int, w: Int, h: Int): Modifier = offset(px(x), px(y)).size(px(w), px(h))

private val DesignW = px(2560)

/**
 * The frame of a 2560 x [designH] mockup home (1208, the car's whole content area, by default):
 * [content] is laid out on the mockup's grid (1280 dp wide) and scaled uniformly to fit,
 * letterboxed in [letterbox].
 */
@Composable
internal fun MockupFrame(
    designH: Int = 1208,
    letterbox: Color = MockupInk.Letterbox,
    fillHeight: Boolean = false, // fork: fluid height, 1208..1440 design px, scaled by width
    content: @Composable BoxScope.() -> Unit,
) {
    if (fillHeight) { // fork: the fluid frame; the default path below is untouched
        FluidMockupFrame(letterbox, content)
        return
    }
    val designHDp = px(designH)
    BoxWithConstraints(Modifier.fillMaxSize().background(letterbox), contentAlignment = Alignment.Center) {
        val sw = if (constraints.hasBoundedWidth) maxWidth / DesignW else 1f
        val sh = if (constraints.hasBoundedHeight) maxHeight / designHDp else sw
        val scale = min(sw, sh)
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density * scale, 1f)) {
            Box(Modifier.size(DesignW, designHDp), content = content)
        }
    }
}

// fork: fluid frame. The mockup grows from 2560 x 1208 up to 2560 x 1440 design px with the height.

private const val DesignWPx = 2560f
private const val DesignHMinPx = 1208f
private const val DesignHMaxPx = 1440f

/** The fluid frame: [scale] by width, [heightPx] design px of content, [extraPx] = heightPx - 1208. */
internal data class FluidFrame(val scale: Float, val heightPx: Float, val extraPx: Float)

/** The frame for an area of [maxWidthDp] x [maxHeightDp]: too short keeps today's min scale. */
internal fun fluidFrame(maxWidthDp: Float, maxHeightDp: Float): FluidFrame {
    val sw = maxWidthDp / (DesignWPx * K)
    val sh = maxHeightDp / (DesignHMinPx * K)
    if (sh < sw) return FluidFrame(sh, DesignHMinPx, 0f)
    val h = (maxHeightDp / sw / K).coerceIn(DesignHMinPx, DesignHMaxPx)
    return FluidFrame(sw, h, h - DesignHMinPx)
}

/** The extra design px of height the fluid frame gives (0 in the fixed frame). */
internal val LocalMockupExtra = staticCompositionLocalOf { 0f }

@Composable
private fun FluidMockupFrame(letterbox: Color, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(letterbox), contentAlignment = Alignment.Center) {
        val w = if (constraints.hasBoundedWidth) maxWidth.value else DesignWPx * K
        val h = if (constraints.hasBoundedHeight) maxHeight.value else DesignHMinPx * K
        val frame = fluidFrame(w, h)
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * frame.scale, 1f),
            LocalMockupExtra provides frame.extraPx,
        ) {
            Box(Modifier.size(DesignW, px(frame.heightPx)), content = content)
        }
    }
}

/** The default inks of the shared parts: letterbox, display cyan and white, the lit-key green. */
internal object MockupInk {
    val Letterbox = Color(0xFF0B0B0C)
    val Cyan = Color(0xFF3CDDF2)
    val White = Color(0xFFECECEA)
    val GreenLed = Color(0xFF35E04A)
}

/**
 * One line of text whose vertical centre is at mockup y [cy]: left-aligned at [x], or centred on
 * it with [centre]. [size] is the font size in mockup pixels; [glow] lights it like the display.
 */
@Composable
internal fun Txt(
    text: String,
    x: Int,
    cy: Int,
    size: Float,
    color: Color = MockupInk.White,
    glow: Boolean = false,
    centre: Boolean = false,
    right: Boolean = false,
    width: Int = 600,
    weight: FontWeight = FontWeight.Normal,
) {
    val h = (size * 1.4f).toInt()
    val left = when {
        centre -> x - width / 2
        right -> x - width
        else -> x
    }
    Box(
        Modifier.at(left, cy - h / 2, width, h),
        contentAlignment = when {
            centre -> Alignment.Center
            right -> Alignment.CenterEnd
            else -> Alignment.CenterStart
        },
    ) {
        Text(
            text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centre) TextAlign.Center else if (right) TextAlign.End else TextAlign.Start,
            style = TextStyle(
                color = color, fontSize = fs(size), fontWeight = weight, fontFamily = FontFamily.SansSerif,
                lineHeight = fs(size * 1.2f), letterSpacing = 0.sp,
                shadow = if (glow) Shadow(color.copy(alpha = 0.7f), blurRadius = size * K * 0.8f) else null,
            ),
        )
    }
}

/** Value and its unit on one baseline, e.g. "824" + " км". */
@Composable
internal fun ValueUnit(value: String, unit: String, x: Int, cy: Int, size: Float, unitSize: Float, color: Color = MockupInk.Cyan) {
    Box(Modifier.at(x, cy - (size * 0.7f).toInt(), 700, (size * 1.4f).toInt()), contentAlignment = Alignment.CenterStart) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Bottom) {
            Text(value, maxLines = 1, softWrap = false, style = valueStyle(size, color))
            Text(unit, maxLines = 1, softWrap = false, style = valueStyle(unitSize, color), modifier = Modifier.offset(y = -px(size * 0.06f)))
        }
    }
}

private fun valueStyle(size: Float, color: Color) = TextStyle(
    color = color, fontSize = fs(size), fontFamily = FontFamily.SansSerif, lineHeight = fs(size * 1.2f),
    shadow = Shadow(color.copy(alpha = 0.7f), blurRadius = size * K * 0.8f),
)

/**
 * Ten-segment level bar as on the mockup: lit blocks in [color] with a light rim, the rest dark
 * with a grey outline. All dark when unknown.
 */
@Composable
internal fun SegmentBar(pct: Int?, color: Color, modifier: Modifier, unlitRim: Color = Color(0xFF6B6F6D)) {
    Canvas(modifier) {
        val n = 10
        val gap = size.width * 0.012f
        val w = (size.width - gap * (n - 1)) / n
        val lit = pct?.let { ((it + 5) / 10).coerceIn(0, n) } ?: 0
        val px1 = 1.dp.toPx()
        for (i in 0 until n) {
            val x = i * (w + gap)
            if (i < lit) {
                drawRect(color, Offset(x, 0f), Size(w, size.height))
                drawRect(Color.White.copy(alpha = 0.25f), Offset(x, 0f), Size(w, size.height * 0.35f))
                drawRect(Color.White.copy(alpha = 0.55f), Offset(x, 0f), Size(w, size.height), style = Stroke(px1))
            } else {
                drawRect(Color(0xFF0B0F0D), Offset(x, 0f), Size(w, size.height))
                drawRect(unlitRim, Offset(x, 0f), Size(w, size.height), style = Stroke(px1))
            }
        }
    }
}

/** The AUTO badge of the climate: an outlined pill, [w] x [h] mockup pixels from ([x], [cy] − h/2). */
@Composable
internal fun AutoBadge(
    x: Int,
    cy: Int,
    w: Int = 88,
    h: Int = 34,
    textSize: Float = 24f,
    rim: Color = MockupInk.Cyan.copy(alpha = 0.6f),
    textColor: Color = MockupInk.Cyan,
) {
    Canvas(Modifier.at(x, cy - h / 2, w, h)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color(0xFF0B3036), cornerRadius = r)
        drawRoundRect(rim, cornerRadius = r, style = Stroke(1.dp.toPx()))
    }
    Txt("AUTO", x + w / 2, cy, textSize, textColor, centre = true, width = w)
}

/** An invisible touch area over a printed key: darker while held, or lit green for a tab key. */
@Composable
internal fun PressArea(
    modifier: Modifier,
    name: String,
    onClick: () -> Unit,
    litOnPress: Boolean = false,
    role: Role? = Role.Button, // fork: null for a key that only answers the touch
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .semantics { contentDescription = name }
            .clickable(interactionSource = source, indication = null, role = role, onClick = onClick),
    ) {
        if (pressed) {
            Canvas(Modifier.fillMaxSize()) {
                val r = CornerRadius(6f * K * density)
                if (litOnPress) {
                    drawRoundRect(MockupInk.GreenLed.copy(alpha = 0.22f), cornerRadius = r)
                    drawRoundRect(MockupInk.GreenLed.copy(alpha = 0.8f), cornerRadius = r, style = Stroke(3f * K * density))
                } else {
                    drawRoundRect(Color.Black.copy(alpha = 0.35f), cornerRadius = r)
                }
            }
        }
    }
}

/** "22°C", "−3°C" (a true minus sign) or "—". */
internal fun Int?.celsius(): String = when {
    this == null -> "—"
    this < 0 -> "−${-this}°C"
    else -> "$this°C"
}

/** "73 482" with a thin space between thousands. */
internal fun groupThousands(n: Int): String = String.format(Locale.US, "%,d", n).replace(',', ' ')

/** Music volume 0..1, read from the system while the screen is started; null when unavailable. */
@Composable
internal fun rememberMusicVolume(): Float? {
    val context = LocalContext.current
    val v = remember { mutableStateOf<Float?>(null) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@LaunchedEffect
        // only while the screen is started: a volume broadcast plus a 10-s fallback poll
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            watchMusicVolume(context) {
                v.value = runCatching {
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else null
                }.getOrNull()
            }
        }
    }
    return v.value
}
