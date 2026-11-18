package com.bydmate.app.ui.home.graphite

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.bydmate.app.R
import com.bydmate.app.ui.home.common.px
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Photos = intArrayOf(
    R.drawable.gr_photo_hill,
    R.drawable.gr_photo_desert,
    R.drawable.gr_photo_front,
    R.drawable.gr_photo_side_night,
    R.drawable.gr_photo_lights,
)

private const val FADE_MS = 1400
private const val ZOOM_MS = 200_000
private const val ZOOM_FROM = 1.02f
private const val ZOOM_TO = 1.08f
private const val ZOOM_STEP_MS = 1_500L

/** Linear 1.02 -> 1.08 over 200 s and back, [elapsedMs] after the start. */
internal fun galleryZoom(elapsedMs: Long): Float {
    val phase = (elapsedMs % (2L * ZOOM_MS)).toFloat() / ZOOM_MS // 0..2
    val t = if (phase <= 1f) phase else 2f - phase
    return ZOOM_FROM + (ZOOM_TO - ZOOM_FROM) * t
}

/** 4x5 colour matrix (android.graphics.ColorMatrix layout): saturation, brightness, contrast about 0.5, then tint. */
internal fun photoMatrix(look: PhotoLook, tint: Color): FloatArray {
    // Row-major 5x5, last row [0 0 0 0 1]; translate column is in 0..255 units.
    fun mul(a: FloatArray, b: FloatArray): FloatArray { // a applied after b
        val r = FloatArray(25)
        for (i in 0 until 5) for (j in 0 until 5) {
            var s = 0f
            for (k in 0 until 5) s += a[i * 5 + k] * b[k * 5 + j]
            r[i * 5 + j] = s
        }
        return r
    }
    fun ident() = FloatArray(25).also { for (i in 0 until 5) it[i * 6] = 1f }

    val s = look.saturation
    val inv = 1f - s
    val (lr, lg, lb) = Triple(0.213f, 0.715f, 0.072f)
    val sat = ident().also {
        it[0] = lr * inv + s; it[1] = lg * inv; it[2] = lb * inv
        it[5] = lr * inv; it[6] = lg * inv + s; it[7] = lb * inv
        it[10] = lr * inv; it[11] = lg * inv; it[12] = lb * inv + s
    }
    val bright = ident().also { it[0] = look.brightness; it[6] = look.brightness; it[12] = look.brightness }
    val c = look.contrast
    val t = 0.5f * (1f - c) * 255f
    val contrast = ident().also {
        it[0] = c; it[6] = c; it[12] = c
        it[4] = t; it[9] = t; it[14] = t
    }
    val tintM = ident().also { it[0] = tint.red; it[6] = tint.green; it[12] = tint.blue }
    val m = mul(tintM, mul(contrast, mul(bright, sat)))
    return FloatArray(20) { m[it] }
}

/** A different photo than [current] (when [count] > 1). */
internal fun nextPhoto(current: Int, count: Int, rnd: Random): Int {
    if (count <= 1) return current
    val n = rnd.nextInt(count - 1)
    return if (n >= current) n + 1 else n
}

/** 2 to 4 minutes between photos. */
internal fun nextDelayMs(rnd: Random): Long = 120_000L + rnd.nextLong(120_001L)

/** The «Leopard 5» photo card: a random photo, a new one every 2-4 minutes, slow zoom. */
@Composable
fun GalleryCard(modifier: Modifier) {
    val p = LocalGr.current
    var index by remember { mutableIntStateOf(Random.nextInt(Photos.size)) }
    LaunchedEffect(Unit) {
        val rnd = Random.Default
        while (true) {
            delay(nextDelayMs(rnd))
            index = nextPhoto(index, Photos.size, rnd)
        }
    }
    val filter = remember(p.look, p.photoTint) { ColorFilter.colorMatrix(ColorMatrix(photoMatrix(p.look, p.photoTint))) }
    // A step every 1.5 s (0.00045 of scale, 0.4 px on the card, invisible) instead of a frame every
    // vsync: the card is redrawn ~0.7 times a second, not 60. Read only in graphicsLayer, so
    // no recomposition; the loop waits on the frame clock, which is paused while the screen is stopped.
    var zoom by remember { mutableFloatStateOf(ZOOM_FROM) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            zoom = galleryZoom(withFrameMillis { it } - start)
            delay(ZOOM_STEP_MS)
        }
    }

    // grCard clips to the card and draws its rim, relief, glass and bevel over the content, so they
    // sit above the photo; a texture under it would never show.
    Box(modifier.grCard(p, photo = true)) {
        Crossfade(
            targetState = index, animationSpec = tween(FADE_MS), label = "photo",
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom },
        ) { i ->
            Image(
                painterResource(Photos[i]), contentDescription = null, contentScale = ContentScale.Crop,
                colorFilter = filter, modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val linear = Brush.verticalGradient(
                    0f to p.photoShade, 0.34f to Color.Transparent, 0.58f to Color.Transparent, 1f to p.photoShadeBottom,
                )
                val clear = p.photoShade.copy(alpha = 0f)
                val rx = size.width * 1.2f
                val ry = size.height * 0.9f
                val radial = Brush.radialGradient(
                    0f to clear, 0.6f to clear, 1f to p.photoShade, center = Offset.Zero, radius = rx,
                )
                onDrawBehind {
                    drawRect(linear)
                    // CSS ellipse "120% 90% at 50% 50%": a circle of radius rx squashed vertically.
                    withTransform({
                        translate(size.width / 2f, size.height / 2f)
                        scale(1f, ry / rx, pivot = Offset.Zero)
                    }) { drawRect(radial, topLeft = Offset(-size.width / 2f - rx, -size.height / 2f - rx), size = androidx.compose.ui.geometry.Size(size.width + 2 * rx, size.height + 2 * rx)) }
                }
            },
        )
        Column(Modifier.fillMaxSize().padding(horizontal = px(34), vertical = px(30))) {
            CardHeader(R.drawable.gr_ic_car, "Leopard 5")
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = px(30), bottom = px(22)),
            horizontalArrangement = Arrangement.spacedBy(px(8)), verticalAlignment = Alignment.CenterVertically,
        ) {
            Photos.indices.forEach { k ->
                val on = k == index
                val w by animateDpAsState(if (on) px(28) else px(8), tween(400), label = "dot")
                Box(
                    Modifier.width(w).height(px(8)).clip(RoundedCornerShape(px(4)))
                        .background(if (on) Color.White else Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}
