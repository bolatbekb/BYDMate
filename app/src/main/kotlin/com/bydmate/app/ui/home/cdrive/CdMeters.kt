@file:Suppress("MagicNumber") // drawn on the backplate's pixel grid (tools/cdrive/LAYOUT.md)

package com.bydmate.app.ui.home.cdrive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.bydmate.app.ui.home.HomeData
import com.bydmate.app.ui.home.hybrid.K
import com.bydmate.app.ui.home.hybrid.MeterNeedle
import com.bydmate.app.ui.home.hybrid.NeedlePainter
import com.bydmate.app.ui.home.hybrid.PrintedScale
import com.bydmate.app.ui.home.hybrid.at
import com.bydmate.app.ui.home.hybrid.fs
import com.bydmate.app.ui.home.hybrid.groupThousands
import com.bydmate.app.ui.home.hybrid.px
import java.util.Locale

/** Engine speed 0…6 ×1000 rpm: the needle angle of each printed number, measured from the hub. */
internal val CdRpmScale = PrintedScale(
    1683.5f, 801.5f, 14f, 127f,
    listOf(0.0 to 189.3f, 1.0 to 161.3f, 2.0 to 128.7f, 3.0 to 90.4f, 4.0 to 51.9f, 5.0 to 18.6f, 6.0 to -9.2f),
)

/** AC compressor power 0.0…3.0 kW, likewise. */
internal val CdCompressorScale = PrintedScale(
    2183.5f, 801.5f, 14f, 126f,
    listOf(0.0 to 189.1f, 0.5 to 161.0f, 1.0 to 128.8f, 1.5 to 90.6f, 2.0 to 52.4f, 2.5 to 19.3f, 3.0 to -9.2f),
)

/** "1 760"; a dash while the engine is off or the speed unknown. */
internal fun rpmText(rpm: Int?): String = if (rpm == null || rpm <= 0) "—" else groupThousands(rpm)

/** "1.2" (never "-0.0"); a dash while unknown. */
internal fun kwText(kw: Double?): String =
    if (kw == null || !kw.isFinite()) "—" else String.format(Locale.US, "%.1f", if (kw <= 0.0) 0.0 else kw)

private val RpmCyan = Color(0xFF82FFFF)
private val KwCyan = Color(0xFF55FFFF)
private val UnitWhite = Color(0xFFF4F4F4)
private val NeedleGlow = Color(0xFFC8FF50)
private val NeedleRoot = Color(0xFFE2EC5C)
private val NeedleMid = Color(0xFFF6FA92)
private val NeedleEdge = Color(0xFF3A3A10)
private val GLOW_STEPS = listOf(14f to 0.035f, 9f to 0.06f, 5f to 0.1f)
private val NeedleTip = Color(0xFFFFFFCC)
private val HubDot = Color(0xFFC4EC3E)

/**
 * The two printed meters: engine speed and AC compressor power, each with its needle on the
 * printed scale and the big value under the hub. Engine off or no data: the needle rests at zero
 * and the value reads «—».
 */
@Composable
internal fun CdMeters(d: HomeData) {
    val rpm = d.engineRpm
    MeterNeedle(
        CdRpmScale, rpm?.let { it.coerceAtLeast(0) / 1000.0 }, 1384, 612,
        Modifier.at(1384, 612, 541, 296).graphicsLayer(), painter = CdNeedle,
    )
    MeterValue(rpmText(rpm), "об/мин", 1701, 850, 41f, 31f, RpmCyan)

    val kw = d.compressorKw?.takeIf { it.isFinite() }
    MeterNeedle(
        CdCompressorScale, kw?.coerceAtLeast(0.0), 1928, 612,
        Modifier.at(1928, 612, 560, 296).graphicsLayer(), painter = CdNeedle,
    )
    MeterValue(kwText(kw), "кВт", 2189, 856, 41f, 31f, KwCyan)
}

/** A glowing number and its white unit on one baseline, the pair centred on mockup x [cx]. */
@Composable
private fun MeterValue(value: String, unit: String, cx: Int, cy: Int, size: Float, unitSize: Float, color: Color) {
    val h = (size * 1.6f).toInt()
    Box(Modifier.at(cx - 200, cy - h / 2, 400, h), contentAlignment = Alignment.Center) {
        Row {
            Text(
                value, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline(),
                style = TextStyle(
                    color = color, fontSize = fs(size), fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp,
                    shadow = Shadow(color.copy(alpha = 0.7f), blurRadius = size * K * 0.8f),
                ),
            )
            Spacer(Modifier.width(px(16)))
            Text(
                unit, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline(),
                style = TextStyle(
                    color = UnitWhite, fontSize = fs(unitSize), fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp,
                    shadow = Shadow(UnitWhite.copy(alpha = 0.35f), blurRadius = unitSize * K * 0.5f),
                ),
            )
        }
    }
}

/**
 * The mockup's needle: a tapered pale-yellow blade with a thin dark outline and a green-yellow
 * glow on its lower side, over a dark hub with a bright upper rim and a yellow-green centre dot.
 */
private val CdNeedle: NeedlePainter = { scale, c, dir, s, alpha ->
    val across = Offset(-dir.y, dir.x)
    val low = if (across.y >= 0f) across else -across // the side facing down the screen
    val root = c + dir * (scale.inner * s)
    val tip = c + dir * (scale.outer * s)

    // a soft, narrow glow on the lower side: wide-to-thin strokes with quickly rising alpha
    for ((w, a) in GLOW_STEPS) {
        drawLine(NeedleGlow.copy(alpha = a * alpha), root + low * (2f * s), tip + low * (1.5f * s), w * s, StrokeCap.Round)
    }

    val blade = Path().apply {
        val r = root - dir * (2f * s)
        moveTo(r.x + across.x * 2.2f * s, r.y + across.y * 2.2f * s)
        lineTo(tip.x + across.x * 0.7f * s, tip.y + across.y * 0.7f * s)
        lineTo(tip.x - across.x * 0.7f * s, tip.y - across.y * 0.7f * s)
        lineTo(r.x - across.x * 2.2f * s, r.y - across.y * 2.2f * s)
        close()
    }
    drawPath(blade, NeedleEdge.copy(alpha = 0.55f * alpha), style = Stroke(1f * s, join = StrokeJoin.Round))
    drawPath(
        blade,
        Brush.linearGradient(0f to NeedleRoot, 0.45f to NeedleMid, 1f to NeedleTip, start = root, end = tip),
        alpha = alpha,
    )

    // the hub: soft shadow, dark disc, bright upper rim, the yellow-green dot
    drawCircle(
        Brush.radialGradient(0f to Color.Black.copy(alpha = 0.7f), 1f to Color.Transparent, center = c + Offset(0f, 2f * s), radius = 17f * s),
        radius = 17f * s, center = c + Offset(0f, 2f * s),
    )
    drawCircle(
        Brush.radialGradient(0f to Color(0xFF3B3E3D), 1f to Color(0xFF111312), center = c - Offset(0f, 4f * s), radius = 14f * s),
        radius = 13f * s, center = c,
    )
    drawCircle(
        Brush.verticalGradient(
            0f to Color(0xFFC9CBC7), 0.45f to Color(0xFF4A4D4B), 1f to Color(0xFF242625),
            startY = c.y - 14f * s, endY = c.y + 14f * s,
        ),
        radius = 12f * s, center = c, style = Stroke(1.6f * s),
    )
    drawCircle(Color(0xFF1C2410), radius = 4f * s, center = c)
    drawCircle(
        Brush.radialGradient(0f to Color(0xFFF4FF9A), 1f to HubDot, center = c - Offset(0.7f * s, 0.7f * s), radius = 3.2f * s),
        radius = 3f * s, center = c,
    )
}
