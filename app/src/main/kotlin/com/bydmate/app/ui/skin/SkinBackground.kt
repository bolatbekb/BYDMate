package com.bydmate.app.ui.skin

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.AccentBlue
import com.bydmate.app.ui.theme.AccentGreen
import com.bydmate.app.ui.theme.NavyDark
import com.bydmate.app.ui.theme.NavyDeep
import com.bydmate.app.ui.theme.SkinState

// fork: the screen background of the active skin. AUTHOR is the original gradient, untouched.

/** Full-screen background; reads the skin in composition, so a switch redraws it. */
@Composable
fun Modifier.skinBackground(): Modifier {
    val base = background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))
    return when (SkinState.current.kit.background) {
        BackgroundStyle.AUTHOR -> base
        BackgroundStyle.GRID -> base.then(gridLines(AccentBlue.copy(alpha = 0.04f)))
        BackgroundStyle.GLOW -> base.then(glow(AccentGreen.copy(alpha = 0.06f)))
        BackgroundStyle.SCANLINES -> base.then(scanlines(Color.White.copy(alpha = 0.03f)))
    }
}

private fun gridLines(color: Color): Modifier = Modifier.drawWithCache {
    val step = 48.dp.toPx()
    onDrawBehind {
        var x = 0f
        while (x < size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

private fun glow(color: Color): Modifier = Modifier.drawWithCache {
    val brush = Brush.radialGradient(
        colors = listOf(color, Color.Transparent),
        center = Offset.Zero,
        radius = maxOf(size.width, size.height) * 0.7f,
    )
    onDrawBehind { drawRect(brush) }
}

private fun scanlines(color: Color): Modifier = Modifier.drawWithCache {
    val step = 4.dp.toPx()
    onDrawBehind {
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}
