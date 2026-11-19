package com.bydmate.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember // fork: cache the skin typography and shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.bydmate.app.ui.skin.LocalUserFontScale // fork: the user's own text scale
import com.bydmate.app.ui.skin.family // fork: skin font family
import com.bydmate.app.ui.skin.skinShapes // fork: skin corner shapes
import com.bydmate.app.ui.skin.skinTypography // fork: skin typography

// fork: a getter, not a val: rebuilt from the active skin on every read (cheap), so Material
// components follow a skin switch along with the app's own tokens.
private val DarkColorScheme get() = darkColorScheme(
    background = NavyDark,
    surface = CardSurface,
    surfaceVariant = CardSurfaceElevated,
    primary = AccentGreen,
    secondary = AccentBlue,
    tertiary = AccentTeal,
    error = SocRed,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = NavyDark,
    onSecondary = NavyDark,
    outline = CardBorder,
    surfaceTint = Color.Transparent
)

/** Multiplies only the sp scale; dp sizes stay put. Returns [base] itself for 1f. */
internal fun scaledDensity(base: Density, fontScale: Float): Density =
    if (fontScale == 1f) base else Density(base.density, base.fontScale * fontScale)

/** In-app text size multiplier, so nested windows (dialogs, popups) can re-apply it. */
val LocalAppFontScale = compositionLocalOf { 1f }

/**
 * Applies the in-app text size setting on top of the system font scale.
 * Always the same group (no branch on 1f): switching the setting must not remount the subtree.
 */
@Composable
fun WithAppFontScale(fontScale: Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalDensity provides scaledDensity(LocalDensity.current, fontScale),
        content = content
    )
}

/**
 * Dialog and popup windows re-provide LocalDensity from their own window, dropping the
 * theme-level override; wrap their content in this to re-apply the setting.
 */
@Composable
fun ScaledDialogContent(content: @Composable () -> Unit) =
    WithAppFontScale(LocalAppFontScale.current, content)

@Composable
fun BYDMateTheme(fontScale: Float = 1f, content: @Composable () -> Unit) {
    // fork: removed the author's CompositionLocalProvider(LocalAppFontScale provides fontScale), WithAppFontScale(fontScale), typography = BYDMateTypography; replaced by the skin lines below
    val kit = SkinState.current.kit // fork: the skin's kit, a snapshot read so a switch recomposes
    val scale = fontScale * kit.fonts.sizeFactor // fork: user scale x the skin's size factor
    val typography = remember(kit) { skinTypography(BYDMateTypography, kit.fonts.text.family()) } // fork: built once per kit
    val shapes = remember(kit) { skinShapes(kit.corners) } // fork: built once per kit
    CompositionLocalProvider(
        LocalUserFontScale provides fontScale, // fork: keep the user's scale apart for neutral homes
        LocalAppFontScale provides scale // fork: the combined value, so dialogs follow
    ) {
        WithAppFontScale(scale) { // fork: combined scale instead of fontScale
            MaterialTheme(
                colorScheme = DarkColorScheme,
                typography = typography, // fork: skin font
                shapes = shapes, // fork: skin corners
                content = content
            )
        }
    }
}
