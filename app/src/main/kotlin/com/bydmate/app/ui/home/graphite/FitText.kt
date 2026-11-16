package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import com.bydmate.app.ui.home.common.fs

internal fun fitFontSize(maxSize: Float, minSize: Float, step: Float = 2f, fits: (Float) -> Boolean): Float {
    var s = maxSize
    while (s > minSize) {
        if (fits(s)) return s
        s -= step
    }
    return minSize
}

/**
 * One line of [text] at the largest size from [maxSize] down to [minSize] (mockup px) that fits
 * the width; only at [minSize] does it ellipsize. Built on a plain Layout (no subcomposition), so
 * it supports intrinsic measurement and is safe inside IntrinsicSize rows.
 *
 * [lineBox]: the line keeps the height an Inter line of [style] has. Android sizes a line that
 * falls back to another font (CJK) by that font's taller metrics, ignoring the line height; such a
 * line is drawn on the Inter line's baseline instead, so a block of these texts lays out the same
 * in every language. A line in Inter alone is unchanged.
 */
@Composable
fun FitText(text: String, style: TextStyle, maxSize: Float, minSize: Float, modifier: Modifier, lineBox: Boolean = false) =
    FitText(AnnotatedString(text), style, maxSize, minSize, modifier, lineBox)

/** [FitText] for styled text (bold spans); sizes in the spans must be left to [style]. */
@Composable
fun FitText(
    text: AnnotatedString, style: TextStyle, maxSize: Float, minSize: Float, modifier: Modifier, lineBox: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val policy = remember(text, style, maxSize, minSize, measurer, lineBox) {
        FitPolicy(measurer, text, style, maxSize, minSize, lineBox)
    }
    Layout(
        modifier = modifier.drawBehind { policy.result?.let { drawText(it, topLeft = Offset(0f, policy.shift)) } },
        measurePolicy = policy,
    )
}

/** Measured for the height and baseline of an Inter line of a style: one Latin line is as tall as any other. */
private const val INTER_LINE = "0"

private class FitPolicy(
    val measurer: TextMeasurer,
    val text: AnnotatedString,
    val style: TextStyle,
    val maxSize: Float,
    val minSize: Float,
    val lineBox: Boolean,
) : MeasurePolicy {
    var result: TextLayoutResult? = null

    /** How far the text is drawn up to sit on the Inter line's baseline; 0 unless [lineBox] trims a line. */
    var shift = 0f

    private fun natural(size: Float): TextLayoutResult =
        measurer.measure(text, style.sized(size), maxLines = 1, softWrap = false)

    private fun layoutFor(maxW: Int): TextLayoutResult {
        if (maxW == Constraints.Infinity) return natural(maxSize)
        val size = fitFontSize(maxSize, minSize) { natural(it).size.width <= maxW }
        return measurer.measure(
            text, style.sized(size), overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
            constraints = Constraints(maxWidth = maxW),
        )
    }

    /** With [lineBox], an Inter line of [r]'s style when [r] is taller than one; else null. */
    private fun interLine(r: TextLayoutResult): TextLayoutResult? {
        if (!lineBox) return null
        val line = measurer.measure(INTER_LINE, r.layoutInput.style, maxLines = 1, softWrap = false)
        return line.takeIf { it.size.height < r.size.height }
    }

    private fun boxHeight(r: TextLayoutResult): Int = interLine(r)?.size?.height ?: r.size.height

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val r = layoutFor(constraints.maxWidth)
        result = r
        val line = interLine(r)
        shift = if (line == null) 0f else line.firstBaseline - r.firstBaseline
        val w = r.size.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val h = (line?.size?.height ?: r.size.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(w, h) {}
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        natural(minSize).size.width

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        natural(maxSize).size.width

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        boxHeight(layoutFor(width))

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        boxHeight(layoutFor(width))
}

/** [this] at [size] mockup px; a set line height keeps grStyle's 1.2 ratio. */
private fun TextStyle.sized(size: Float): TextStyle =
    copy(fontSize = fs(size), lineHeight = if (lineHeight.isSpecified) fs(size * 1.2f) else lineHeight)
