package com.bydmate.app.ui.skin

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
// GRAPHITE is the Graphite home's own background in its live scheme, instead of the gradient.

/**
 * Full-screen background; reads the skin in composition, so a switch redraws it. A pattern is
 * remembered per colour (so per palette) in its style's branch: a fresh drawWithCache lambda is an
 * unequal modifier, and the dashboard recomposes this scope on every live-data emission.
 */
@Composable
fun Modifier.skinBackground(): Modifier = when (SkinState.current.kit.background) {
    BackgroundStyle.AUTHOR -> authorGradient()
    BackgroundStyle.GRID -> authorGradient().then(rememberPattern(AccentBlue.copy(alpha = 0.04f), ::gridLines))
    BackgroundStyle.GLOW -> authorGradient().then(rememberPattern(AccentGreen.copy(alpha = 0.06f), ::glow))
    BackgroundStyle.SCANLINES -> authorGradient().then(rememberPattern(Color.White.copy(alpha = 0.03f), ::scanlines))
    BackgroundStyle.GRAPHITE -> graphiteSkinBackground()
}

/** [pattern] in [color], kept while the colour stays; each call site, so each style, has its own slot. */
@Composable
private fun rememberPattern(color: Color, pattern: (Color) -> Modifier): Modifier = remember(color) { pattern(color) }

private fun Modifier.authorGradient(): Modifier = background(Brush.verticalGradient(listOf(NavyDark, NavyDeep)))

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
