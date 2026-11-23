package com.bydmate.app.fork.cells.ui.views

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp

// What the views' Canvas charts share: their labels and a clamp that never throws. fork-local.

/** A text measurer and the base style of a chart's labels (tabular digits). */
internal class Labels(val measurer: TextMeasurer, val base: TextStyle) {
    fun measure(text: String, sp: Float, color: Color, bold: Boolean = false): TextLayoutResult =
        measurer.measure(text, base.merge(TextStyle(fontSize = sp.sp, color = color, fontWeight = if (bold) FontWeight.SemiBold else null)))

    /** Centred lines, wrapped at [maxWidth] px. */
    fun centered(text: String, sp: Float, color: Color, maxWidth: Int): TextLayoutResult =
        measurer.measure(
            text,
            base.merge(TextStyle(fontSize = sp.sp, color = color, textAlign = TextAlign.Center)),
            constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(1)),
        )
}

/** [this] kept within [lo]..[hi]; [lo] when the range is empty (a chart smaller than its label), never an exception. */
internal fun Float.within(lo: Float, hi: Float): Float = if (hi <= lo) lo else coerceIn(lo, hi)
