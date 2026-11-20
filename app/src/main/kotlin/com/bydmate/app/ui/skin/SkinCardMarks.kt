package com.bydmate.app.ui.skin

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

// fork: the card marks of SkinCard.kt (the STRIPE bar, the BRACKETS corner marks), moved out unchanged.

internal fun stripe(color: Color, width: Dp, top: Boolean, shape: Shape): Modifier = Modifier.drawWithCache {
    val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    val w = width.toPx()
    val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - w
    val origin = if (top) Offset.Zero else Offset(left, 0f)
    val bar = if (top) Size(size.width, w) else Size(w, size.height)
    onDrawWithContent {
        drawContent()
        clipPath(clip) { drawRect(color, topLeft = origin, size = bar) }
    }
}

/** Four L-marks (CardBracketLength long, CardBracketThickness thick) on the corners of the bounds. */
internal fun brackets(color: Color): Modifier = Modifier.drawWithCache {
    val len = CardBracketLength.toPx()
    val t = CardBracketThickness.toPx()
    val w = size.width
    val h = size.height
    val marks = Path().apply {
        addRect(Rect(0f, 0f, len, t)); addRect(Rect(0f, 0f, t, len))
        addRect(Rect(w - len, 0f, w, t)); addRect(Rect(w - t, 0f, w, len))
        addRect(Rect(0f, h - t, len, h)); addRect(Rect(0f, h - len, t, h))
        addRect(Rect(w - len, h - t, w, h)); addRect(Rect(w - t, h - len, w, h))
    }
    onDrawWithContent {
        drawContent()
        drawPath(marks, color)
    }
}
