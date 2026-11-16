package com.bydmate.app.ui.home.graphite

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import com.bydmate.app.ui.home.hybrid.fs

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
 * the width; only at [minSize] does it ellipsize.
 */
@Composable
fun FitText(text: String, style: TextStyle, maxSize: Float, minSize: Float, modifier: Modifier) =
    FitText(AnnotatedString(text), style, maxSize, minSize, modifier)

/** [FitText] for styled text (bold spans); sizes in the spans must be left to [style]. */
@Composable
fun FitText(text: AnnotatedString, style: TextStyle, maxSize: Float, minSize: Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val size = remember(text, style, maxSize, minSize, maxW) {
            if (!constraints.hasBoundedWidth) maxSize else fitFontSize(maxSize, minSize) { s ->
                measurer.measure(text, style.sized(s), maxLines = 1, softWrap = false).size.width <= maxW
            }
        }
        Text(text, style = style.sized(size), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** [this] at [size] mockup px; a set line height keeps grStyle's 1.2 ratio. */
private fun TextStyle.sized(size: Float): TextStyle =
    copy(fontSize = fs(size), lineHeight = if (lineHeight.isSpecified) fs(size * 1.2f) else lineHeight)
