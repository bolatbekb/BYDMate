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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.Slider as M3Slider

// fork: drop-in for the M3 Slider (same name, parameters and defaults as M3 1.3.1). MATERIAL
// passes every argument straight to M3. THIN and SEGMENTED go through the M3 thumb and track
// slots, so the value, steps, callbacks, gestures, semantics and touch target stay M3's own;
// only the drawing changes. Colours are the caller's SliderColors.

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
    when (SkinState.current.kit.controls.slider) {
        SliderStyle.MATERIAL -> M3Slider(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
            valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished,
            colors = colors, interactionSource = interactionSource,
        )
        SliderStyle.THIN -> M3Slider(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
            onValueChangeFinished = onValueChangeFinished, colors = colors, interactionSource = interactionSource,
            steps = steps,
            thumb = { ThinThumb(interactionSource, colors, enabled) },
            track = { ThinTrack(it, colors, enabled) },
            valueRange = valueRange,
        )
        SliderStyle.SEGMENTED -> M3Slider(
            value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
            onValueChangeFinished = onValueChangeFinished, colors = colors, interactionSource = interactionSource,
            steps = steps,
            thumb = { SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled) },
            track = { SegmentedTrack(it, colors, enabled) },
            valueRange = valueRange,
        )
    }
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
