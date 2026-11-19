package com.bydmate.app.ui.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DividerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.bydmate.app.ui.theme.SkinState
import androidx.compose.material3.HorizontalDivider as M3HorizontalDivider

// fork: drop-in for the M3 HorizontalDivider (same name, parameters and defaults as M3 1.3.1).
// It follows the card style: GLASS fades its ends, BORDER_ONLY draws CardBorder at full alpha,
// every other style passes the caller's arguments straight to M3.

private const val FADE_END = 0.2f

@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color,
) {
    // a leaf with no state: GLASS draws its own line, every other look is one M3 call
    val look = dividerLook(SkinState.current.kit.controls.card)
    if (look == DividerLook.FADED_ENDS) {
        val brush = remember(color) {
            Brush.horizontalGradient(0f to Color.Transparent, FADE_END to color, 1f - FADE_END to color, 1f to Color.Transparent)
        }
        // the M3 divider's own drawing, with the brush for the colour
        Canvas(modifier.fillMaxWidth().height(thickness)) {
            val y = thickness.toPx() / 2f
            drawLine(brush, Offset(0f, y), Offset(size.width, y), thickness.toPx())
        }
    } else {
        M3HorizontalDivider(
            modifier = modifier,
            thickness = thickness,
            color = if (look == DividerLook.CARD_BORDER) SkinState.palette.cardBorder else color,
        )
    }
}
