package com.bydmate.app.ui.skin

import androidx.annotation.IntRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.SkinPalette
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.Slider as M3Slider

// fork: drop-in for the M3 Slider (same name, parameters and defaults as M3 1.3.1). MATERIAL
// passes every argument straight to M3, with M3's own thumb and track. THIN and SEGMENTED go
// through the same M3 thumb and track slots, so the value, steps, callbacks, gestures,
// semantics and touch target stay M3's own; only the drawing changes. Colours are the caller's
// SliderColors; GRADIENT and GLOW (Telemetry, Volt) take theirs from the skin palette, like the
// styled switches, and dim to the disabled alpha.

private val ThinTrackHeight = 2.dp
private val ThinThumbSize = 14.dp
private val ThinHaloRadius = 12.dp
private const val THIN_HALO_ALPHA = 0.24f
private val SegmentHeight = 8.dp
private val SegmentGap = 3.dp
private val SegmentCorner = 1.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0) steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    // one M3Slider call whatever the style, so a skin switch keeps the slider (a branch per
    // style would be three call sites); the style picks the thumb and the track. MATERIAL's two
    // are the ones M3 1.3.1's value overload passes, with the same arguments.
    val style = SkinState.current.kit.controls.slider
    M3Slider(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        onValueChangeFinished = onValueChangeFinished, colors = colors, interactionSource = interactionSource,
        steps = steps,
        thumb = {
            when (style) {
                SliderStyle.THIN -> ThinThumb(interactionSource, colors, enabled)
                SliderStyle.GRADIENT, SliderStyle.GLOW -> LitThumb(style, interactionSource, enabled)
                SliderStyle.MATERIAL, SliderStyle.SEGMENTED ->
                    SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled)
            }
        },
        track = { state ->
            when (style) {
                SliderStyle.MATERIAL -> SliderDefaults.Track(colors = colors, enabled = enabled, sliderState = state)
                SliderStyle.THIN -> ThinTrack(state, colors, enabled)
                SliderStyle.SEGMENTED -> SegmentedTrack(state, colors, enabled)
                SliderStyle.GRADIENT, SliderStyle.GLOW -> LitTrack(style, state, enabled)
            }
        },
        valueRange = valueRange,
    )
}

/** A 14 dp dot; a soft halo while it is pressed or dragged. */
@Composable
private fun ThinThumb(interactionSource: MutableInteractionSource, colors: SliderColors, enabled: Boolean) {
    val pressed = interactionSource.collectIsPressedAsState()
    val dragged = interactionSource.collectIsDraggedAsState()
    val color = if (enabled) colors.thumbColor else colors.disabledThumbColor
    Spacer(
        Modifier
            .size(ThinThumbSize)
            .hoverable(interactionSource = interactionSource)
            .drawBehind {
                if (pressed.value || dragged.value) {
                    drawCircle(color.copy(alpha = THIN_HALO_ALPHA), ThinHaloRadius.toPx())
                }
                drawCircle(color)
            },
    )
}

/** A 2 dp line, the active part up to the value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinTrack(state: SliderState, colors: SliderColors, enabled: Boolean) {
    val active = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    Canvas(Modifier.fillMaxWidth().height(ThinTrackHeight)) {
        val f = sliderFraction(state.value, state.valueRange)
        val y = size.height / 2f
        val stroke = size.height
        val rtl = layoutDirection == LayoutDirection.Rtl
        val split = if (rtl) size.width * (1f - f) else size.width * f
        drawLine(inactive, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        if (f > 0f) {
            val from = if (rtl) split else 0f
            val to = if (rtl) size.width else split
            drawLine(active, Offset(from, y), Offset(to, y), stroke, StrokeCap.Round)
        }
    }
}

/** [SLIDER_SEGMENTS] segments, lit up to the value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedTrack(state: SliderState, colors: SliderColors, enabled: Boolean) {
    val active = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    Canvas(Modifier.fillMaxWidth().height(SegmentHeight)) {
        val lit = litSegments(sliderFraction(state.value, state.valueRange))
        val gap = SegmentGap.toPx()
        val w = (size.width - gap * (SLIDER_SEGMENTS - 1)) / SLIDER_SEGMENTS
        val segment = Size(w, size.height)
        val radius = CornerRadius(SegmentCorner.toPx())
        val rtl = layoutDirection == LayoutDirection.Rtl
        for (i in 0 until SLIDER_SEGMENTS) {
            val x = if (rtl) size.width - (i + 1) * w - i * gap else i * (w + gap)
            drawRoundRect(if (i < lit) active else inactive, Offset(x, 0f), segment, radius)
        }
    }
}

private val GradientTrackHeight = 6.dp
private val GradientThumbSize = 22.dp
private val GradientThumbRing = 3.dp
private val GlowTrackHeight = 4.dp
private val GlowThumbSize = 20.dp
private val SliderGlowSpread = 10.dp
private const val SLIDER_GLOW_ALPHA = 0.5f

/** The thumb of GRADIENT (a white disc in an accent ring, 22 dp) or GLOW (an accent disc with a glow, 20 dp). */
@Composable
private fun LitThumb(style: SliderStyle, interactionSource: MutableInteractionSource, enabled: Boolean) {
    val p = SkinState.palette
    val gradient = style == SliderStyle.GRADIENT
    val size = if (gradient) GradientThumbSize else GlowThumbSize
    Spacer(
        Modifier
            .size(size)
            .hoverable(interactionSource = interactionSource)
            .then(if (enabled) Modifier else Modifier.alpha(SWITCH_DISABLED_ALPHA))
            .then(remember(p, gradient) { litThumbDraw(p, gradient) }),
    )
}

private fun litThumbDraw(p: SkinPalette, gradient: Boolean): Modifier = Modifier.drawWithCache {
    val r = size.minDimension / 2f
    val ring = GradientThumbRing.toPx()
    val spread = SliderGlowSpread.toPx()
    val glow = if (gradient) null else Brush.radialGradient(listOf(p.accentGreen.copy(alpha = SLIDER_GLOW_ALPHA), Color.Transparent), Offset(size.width / 2f, size.height / 2f), r + spread)
    onDrawBehind {
        if (gradient) {
            drawCircle(p.accentGreen, r)
            drawCircle(Color.White, r - ring)
        } else {
            drawCircle(glow!!, r + spread)
            drawCircle(p.accentGreen, r)
        }
    }
}

/** GRADIENT: 6 dp, round ends, accentOrange to accentGreen up to the value. GLOW: 4 dp, accentGreen with a glow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LitTrack(style: SliderStyle, state: SliderState, enabled: Boolean) {
    val p = SkinState.palette
    val gradient = style == SliderStyle.GRADIENT
    val height = if (gradient) GradientTrackHeight else GlowTrackHeight
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (enabled) Modifier else Modifier.alpha(SWITCH_DISABLED_ALPHA)),
    ) {
        val f = sliderFraction(state.value, state.valueRange)
        val y = size.height / 2f
        val rtl = layoutDirection == LayoutDirection.Rtl
        val split = if (rtl) size.width * (1f - f) else size.width * f
        drawLine(p.cardBorder, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
        if (f > 0f) {
            val from = if (rtl) split else 0f
            val to = if (rtl) size.width else split
            if (gradient) {
                val colors = listOf(p.accentOrange, p.accentGreen)
                val brush = Brush.horizontalGradient(
                    if (rtl) colors.reversed() else colors, startX = from, endX = to,
                )
                drawLine(brush, Offset(from, y), Offset(to, y), size.height, StrokeCap.Round)
            } else {
                // the glow follows the lit part, a round-ended bar of the track's height
                drawRectGlow(
                    Offset(from, 0f), Size(to - from, size.height), size.height / 2f,
                    p.accentGreen.copy(alpha = SLIDER_GLOW_ALPHA), SliderGlowSpread.toPx(),
                )
                drawLine(p.accentGreen, Offset(from, y), Offset(to, y), size.height, StrokeCap.Round)
            }
        }
    }
}
