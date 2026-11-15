package com.bydmate.app.ui.home

import android.content.Context
import androidx.annotation.DrawableRes
import com.bydmate.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.lerp as lerpColor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * A cassette seen through a deck's narrow tape window. A [ReelCassette] skin is either photo
 * reels cut from the deck's own artwork (they turn over tape packs printed on it) or a cassette
 * drawn in code from its colours (tape packs, chrome flanges, hubs, clamps), which covers the
 * whole window. Add a skin to [ReelCassettes] and it joins the rotation.
 */

/**
 * The colours of one drawn cassette. [tapeDark] / [tapeLight] shade the wound tape, [flange]
 * the chrome reel ring, [hub] the black hub with its spokes, [clamp] the two tape clamps on the
 * outer side of each ring, [back] the shell seen behind the reels. [side] and [minutes] are
 * printed beside the window.
 */
data class ReelCassette(
    val name: String,
    val tapeDark: Color,
    val tapeLight: Color,
    val flange: Color,
    val hub: Color,
    val clamp: Color,
    val back: Color,
    val side: String = "A",
    val minutes: Int = 90,
    /** Photo reels (square, the reel centred); null: the cassette is drawn from the colours above. */
    @DrawableRes val reelLeft: Int? = null,
    @DrawableRes val reelRight: Int? = null,
) {
    val photo: Boolean get() = reelLeft != null && reelRight != null
}

/** Every cassette for the tape window; the first is the RS deck's own, photographed in its artwork. */
val ReelCassettes = listOf(
    ReelCassette(
        name = "RS Chrome Amber",
        tapeDark = Color(0xFF1E140D),
        tapeLight = Color(0xFF5A3D27),
        flange = Color(0xFFB9BEC4),
        hub = Color(0xFF141518),
        clamp = Color(0xFFE6A11E),
        back = Color(0xFF15120F),
        reelLeft = R.drawable.rs_reel_l,
        reelRight = R.drawable.rs_reel_r,
    ),
)

/** Which drawn cassette is in the deck: the next one on every app start. */
object ReelCassetteRotation {
    private const val PREFS = "bydmate_cassette"
    private const val KEY = "reel_index"

    var index by mutableIntStateOf(0)
        private set

    val current: ReelCassette get() = ReelCassettes[index % ReelCassettes.size]

    fun init(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val n = prefs.getInt(KEY, 0).coerceAtLeast(0)
            index = n % ReelCassettes.size
            prefs.edit().putInt(KEY, (n + 1) % ReelCassettes.size).apply()
        }
    }
}

/**
 * The tape window: both reels turn by [hubAngle] degrees (read at draw time, so spinning only
 * redraws this canvas); the left pack shrinks and the right one grows with [progress].
 */
@Composable
fun ReelWindow(
    progress: Float,
    hubAngle: () -> Float,
    modifier: Modifier = Modifier,
    cassette: ReelCassette = ReelCassetteRotation.current,
) {
    Canvas(modifier.semantics { contentDescription = "Кассета" }) {
        val p = progress.coerceIn(0f, 1f)
        val h = size.height
        val flange = h * 0.43f
        val cl = Offset(size.width * 0.29f, h / 2)
        val cr = Offset(size.width * 0.77f, h / 2)
        clipRect {
            drawRect(Brush.verticalGradient(listOf(cassette.back, Color.Black, cassette.back)))
            // packs, the full one reaches past the window edges like on the mockup
            val minR = flange * 1.04f
            val maxR = h * 0.95f
            drawTapePack(cassette, cl, lerp(maxR, minR, p))
            drawTapePack(cassette, cr, lerp(minR, maxR, p))
            val a = -hubAngle()
            drawReel(cassette, cl, flange, a, clampOnLeft = true)
            drawReel(cassette, cr, flange, a, clampOnLeft = false)
            // window glass: smoke and a top highlight
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.25f))))
        }
        drawRoundRect(Color.Black.copy(alpha = 0.8f), style = Stroke(2f), cornerRadius = CornerRadius(3f))
    }
}

private fun DrawScope.drawTapePack(c: ReelCassette, at: Offset, r: Float) {
    drawCircle(
        Brush.radialGradient(
            0f to c.tapeDark, 0.55f to c.tapeLight, 0.8f to lerpColor(c.tapeLight, c.tapeDark, 0.3f), 1f to c.tapeDark,
            center = at, radius = r,
        ),
        r, at,
    )
    var rr = r * 0.4f
    var i = 0
    val step = (r / 18f).coerceAtLeast(2f)
    while (rr < r - 1f) {
        drawCircle(
            if (i % 3 == 0) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
            rr, at, style = Stroke(1.2f),
        )
        rr += step
        i++
    }
    drawCircle(Color.Black.copy(alpha = 0.55f), r, at, style = Stroke(1.5f))
}

/** Chrome flange, clamp, black hub with six spokes and a centre pin, turned by [angle]. */
private fun DrawScope.drawReel(c: ReelCassette, at: Offset, r: Float, angle: Float, clampOnLeft: Boolean) {
    drawCircle(Color.Black.copy(alpha = 0.5f), r * 1.04f, at + Offset(0f, r * 0.05f))
    drawCircle(
        Brush.sweepGradient(
            listOf(c.flange, lerpColor(c.flange, Color.Black, 0.45f), c.flange, lerpColor(c.flange, Color.White, 0.5f), c.flange),
            at,
        ),
        r, at,
    )
    drawCircle(lerpColor(c.flange, Color.Black, 0.3f), r * 0.72f, at, style = Stroke(r * 0.05f))
    drawCircle(c.hub, r * 0.66f, at)
    rotate(angle, at) {
        // clamp: an amber band on the ring, starting on the outer side as on the mockup
        val start = if (clampOnLeft) 145f else -35f
        drawArc(c.clamp, start, 70f, false, at - Offset(r * 0.86f, r * 0.86f), Size(r * 1.72f, r * 1.72f), style = Stroke(r * 0.15f))
        for (k in 0 until 6) {
            val t = Math.toRadians(k * 60.0).toFloat()
            val d = Offset(cos(t), sin(t))
            drawLine(lerpColor(c.hub, Color.White, 0.18f), at + d * (r * 0.22f), at + d * (r * 0.58f), r * 0.1f)
        }
        drawCircle(lerpColor(c.hub, Color.White, 0.12f), r * 0.26f, at, style = Stroke(r * 0.06f))
    }
    drawCircle(lerpColor(c.hub, Color.White, 0.55f), min(r * 0.08f, 6f), at)
    drawCircle(Color.White.copy(alpha = 0.12f), r * 0.98f, at, style = Stroke(1f))
}
