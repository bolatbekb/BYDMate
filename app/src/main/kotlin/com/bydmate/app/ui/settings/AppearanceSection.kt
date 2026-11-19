package com.bydmate.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** Rail icon; lives here so SettingsScreen.kt needs no extra import. */
internal val AppearanceSectionIcon: ImageVector get() = Icons.Outlined.Palette

/** Settings › Appearance: home screen style, app colours, then the Graphite look when it applies. fork-local. */
@Composable
internal fun AppearanceSection() {
    HomeStylePickerBlock()
    SkinPickerBlock()
    GraphiteLookBlock()
}
