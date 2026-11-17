package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.offset
import com.bydmate.app.R
import com.bydmate.app.ui.home.common.fs
import com.bydmate.app.ui.home.common.px

/*
 * The Graphite home's visual language: palette, Inter, the card and its parts. Every size is in
 * mockup pixels (tools/graphite/design/SPEC.md), converted by px()/fs() inside MockupFrame.
 */

/** The home palette of the chosen Graphite scheme; GraphiteHome provides it. */
val LocalGr = staticCompositionLocalOf { GrDark }

private val InterWeights = intArrayOf(250, 300, 400, 450, 500, 600)

/** Inter Variable at the weights the mockup uses; only for ui/home/graphite. */
@OptIn(ExperimentalTextApi::class)
val GrInter: FontFamily = FontFamily(
    InterWeights.map { w ->
        Font(R.font.gr_inter, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** Rajdhani Medium, the cluster's numerals (BYD DiLink); digits and Latin only, no Cyrillic. */
val GrRajdhani: FontFamily = FontFamily(Font(R.font.gr_rajdhani, weight = FontWeight.Medium))

/** Numbers this big (mockup px) take Rajdhani under the DiLink font. */
private const val NUMERAL_MIN_SIZE = 46f

/**
 * Inter text: [size] and [tracking] in mockup px. The mockup sets "cv11" and "ss01" on the whole
 * page; [tnum] adds tabular figures.
 */
fun grStyle(size: Float, weight: Int, tracking: Float = 0f, color: Color, tnum: Boolean = true): TextStyle {
    // DiLink: big tabular numbers in Rajdhani (as the cluster's speed), all other text in Roboto.
    val dilink = GraphiteLookState.font == GrFont.DILINK
    val numeral = dilink && tnum && size >= NUMERAL_MIN_SIZE
    return grTextStyle(
        size, if (numeral) 500 else weight, if (dilink) 0f else tracking, color, tnum,
        when {
            numeral -> GrRajdhani
            dilink -> FontFamily.Default
            else -> GrInter
        },
    )
}

private fun grTextStyle(size: Float, weight: Int, tracking: Float, color: Color, tnum: Boolean, family: FontFamily) = TextStyle(
    color = color,
    fontFamily = family,
    fontWeight = FontWeight(weight),
    fontSize = fs(size),
    letterSpacing = fs(tracking),
    lineHeight = fs(size * 1.2f),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    fontFeatureSettings = if (tnum) "tnum, cv11, ss01" else "cv11, ss01",
)

/**
 * Card with the common padding (30 top/bottom, 34 sides): every header sits at the same height.
 * [bottom] lets a crowded card take a little off its bottom only.
 */
@Composable
fun CardBody(modifier: Modifier, bottom: Int = 30, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.grCard(LocalGr.current).padding(start = px(34), top = px(30), end = px(34), bottom = px(bottom)),
        content = content,
    )
}

/** Card header: icon 26 TextTertiary, gap 12, title 23/450/0.1 TextSecondary; [trailing] on the right. */
@Composable
fun CardHeader(
    icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    titleStyle: TextStyle? = null,
    iconTint: Color? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val p = LocalGr.current
    Row(modifier.fillMaxWidth().height(px(34)), verticalAlignment = Alignment.CenterVertically) {
        GrIcon(icon, 26f, iconTint ?: p.textTertiary)
        Spacer(Modifier.width(px(12)))
        Text(
            title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            style = titleStyle ?: grStyle(23f, 450, 0.1f, titleColor ?: p.textSecondary, tnum = false),
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** A gr_ic_* vector, [size] mockup px, coloured by [tint]. */
@Composable
fun GrIcon(id: Int, size: Float, tint: Color) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = Modifier.size(px(size)))
}

/**
 * Top-bar chip (SPEC §5): height 56, radius 28, padding 18 left / 24 right, gap 12 after [lead];
 * [text] 22/500 TextPrimary, then [note] 20/400 TextTertiary after a space.
 */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, note: String? = null, lead: (@Composable () -> Unit)? = null) {
    val p = LocalGr.current
    Row(
        modifier.height(px(56)).grPill(p.chip, p.chipEdge, p.edgeStyle, 28f).padding(start = px(18), end = px(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lead != null) {
            lead()
            Spacer(Modifier.width(px(12)))
        }
        Text(chipText(text, note, p), maxLines = 1, softWrap = false, style = grStyle(22f, 500, color = p.textPrimary))
    }
}

private fun chipText(text: String, note: String?, p: GrPalette): AnnotatedString = buildAnnotatedString {
    append(text)
    if (note != null) {
        withStyle(SpanStyle(color = p.textTertiary, fontWeight = FontWeight(400), fontSize = fs(20f))) { append(" $note") }
    }
}

/**
 * A sunken block (volume, tabs, footer): Inset fill; on Graphite / Dark an `inset 0 1px 3px`
 * InsetEdge shadow, on Edge a 1 px InsetEdge contour; the relief in its reduced form.
 */
@Composable
fun InsetBox(radius: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = LocalGr.current
    Box(modifier.clip(RoundedCornerShape(px(radius))).background(p.inset).insetRim(p, radius), content = content)
}

@Composable
private fun Modifier.insetRim(p: GrPalette, radius: Float): Modifier {
    val relief = LocalGrLook.current.relief
    return this then remember(p, radius, relief) { rimModifier(p, radius, relief) }
}

private fun rimModifier(p: GrPalette, radius: Float, relief: GrRelief): Modifier = Modifier.drawWithCache {
    val u = px(1).toPx()
    val r = px(radius).toPx()
    val shade = smallRelief(relief)
    if (p.edgeStyle) {
        onDrawWithContent {
            shade?.let { drawRect(it) }
            drawContent()
            drawRoundRect(
                p.insetEdge, topLeft = Offset(u / 2, u / 2), size = Size(size.width - u, size.height - u),
                cornerRadius = CornerRadius(r - u / 2), style = Stroke(u),
            )
        }
    } else {
        val path = insetShadowPath(size, r, u, 8 * u)
        val paint = blurPaint(p.insetEdge, 3 * u)
        onDrawBehind {
            shade?.let { drawRect(it) }
            drawNativePath(path, paint)
        }
    }
}

/**
 * Lets [input] take touches [margin] mockup px above and below this element without moving or
 * resizing anything: the element lays out at its own size and the input sits on a box taller by
 * the margins. Compose clips touches only at clip layers, not at a parent's bounds.
 */
internal fun Modifier.touchMarginVertical(margin: Float, input: Modifier): Modifier = this
    .layout { measurable, constraints ->
        val m = px(margin).roundToPx()
        val placeable = measurable.measure(constraints.offset(vertical = 2 * m))
        layout(placeable.width, (placeable.height - 2 * m).coerceAtLeast(0)) { placeable.place(0, -m) }
    }
    .then(input)
    .padding(vertical = px(margin))
