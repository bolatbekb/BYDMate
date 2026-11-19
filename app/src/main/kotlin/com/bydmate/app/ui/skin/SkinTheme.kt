package com.bydmate.app.ui.skin

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.BYDMateTypography
import com.bydmate.app.ui.theme.LocalAppFontScale

/** The user's own text size, without the skin's size factor. */
val LocalUserFontScale = compositionLocalOf { 1f }

/** dp x scale, never negative. */
fun mapCornerDp(corners: SkinCorners, dp: Dp): Dp = (dp.value * corners.scale).coerceAtLeast(0f).dp

private fun corner(corners: SkinCorners, dp: Int): CornerBasedShape {
    val size = mapCornerDp(corners, dp.dp)
    return when (corners.style) {
        CornerStyle.ROUNDED -> RoundedCornerShape(size)
        CornerStyle.CUT -> CutCornerShape(size)
    }
}

/** The M3 default sizes (4/8/12/16/28 dp) through [mapCornerDp]; the classic kit equals [Shapes]. */
fun skinShapes(corners: SkinCorners): Shapes = Shapes(
    extraSmall = corner(corners, 4),
    small = corner(corners, 8),
    medium = corner(corners, 12),
    large = corner(corners, 16),
    extraLarge = corner(corners, 28),
)

/**
 * The retro stands and the Graphite home: the author's typography and shapes, the user's text
 * scale alone (the skin's size factor undone), whatever the skin.
 */
@Composable
fun SkinNeutralTheme(content: @Composable () -> Unit) {
    val factor = com.bydmate.app.ui.theme.SkinState.current.kit.fonts.sizeFactor
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides if (factor == 1f) d else Density(d.density, d.fontScale / factor),
        LocalAppFontScale provides LocalUserFontScale.current,
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            typography = BYDMateTypography,
            shapes = Shapes(),
            content = content,
        )
    }
}
