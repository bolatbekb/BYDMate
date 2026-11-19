@file:Suppress("FunctionNaming")

package com.bydmate.app.ui.skin

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.foundation.shape.RoundedCornerShape as FoundationRounded

// fork: drop-ins for the foundation RoundedCornerShape factories. The shape follows the active
// skin's corners (rounded or cut, scaled); the classic kit returns the foundation shape itself.

private fun active(): SkinCorners = SkinState.current.kit.corners

/** The shape of one corner size for [corners]; the classic kit gives the foundation rounded shape. */
fun shapeFor(corners: SkinCorners, dp: Dp): CornerBasedShape {
    val size = mapCornerDp(corners, dp)
    return when (corners.style) {
        CornerStyle.ROUNDED -> FoundationRounded(size)
        CornerStyle.CUT -> CutCornerShape(size)
    }
}

private fun scaled(corners: SkinCorners, size: CornerSize): CornerSize =
    if (corners.scale == 1f) size else ScaledCornerSize(size, corners.scale)

private data class ScaledCornerSize(val base: CornerSize, val factor: Float) : CornerSize {
    override fun toPx(shapeSize: androidx.compose.ui.geometry.Size, density: androidx.compose.ui.unit.Density): Float =
        (base.toPx(shapeSize, density) * factor).coerceAtLeast(0f)
}

private fun shapeFor(corners: SkinCorners, size: CornerSize): CornerBasedShape {
    val s = scaled(corners, size)
    return when (corners.style) {
        CornerStyle.ROUNDED -> FoundationRounded(s)
        CornerStyle.CUT -> CutCornerShape(s)
    }
}

@Suppress("FunctionNaming") // drop-in for the foundation factory
fun RoundedCornerShape(size: Dp): CornerBasedShape = shapeFor(active(), size)

@Suppress("FunctionNaming") // drop-in for the foundation factory
fun RoundedCornerShape(percent: Int): CornerBasedShape = when (active().style) {
    CornerStyle.ROUNDED -> FoundationRounded(percent)
    CornerStyle.CUT -> CutCornerShape(percent)
}

@Suppress("FunctionNaming") // drop-in for the foundation factory
fun RoundedCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): CornerBasedShape {
    val c = active()
    return when (c.style) {
        CornerStyle.ROUNDED -> FoundationRounded(
            mapCornerDp(c, topStart), mapCornerDp(c, topEnd), mapCornerDp(c, bottomEnd), mapCornerDp(c, bottomStart),
        )
        CornerStyle.CUT -> CutCornerShape(
            mapCornerDp(c, topStart), mapCornerDp(c, topEnd), mapCornerDp(c, bottomEnd), mapCornerDp(c, bottomStart),
        )
    }
}

@Suppress("FunctionNaming") // drop-in for the foundation factory
fun RoundedCornerShape(size: Float): CornerBasedShape =
    shapeFor(active(), androidx.compose.foundation.shape.CornerSize(size))

@Suppress("FunctionNaming") // drop-in for the foundation factory
fun RoundedCornerShape(corner: CornerSize): CornerBasedShape = shapeFor(active(), corner)
