package com.bydmate.app.ui.theme

import androidx.compose.ui.graphics.Color

// fork: every token reads the active skin (Skins.kt). The names stay the author's so the
// screens compile unchanged; reading one inside a composable subscribes it to skin changes.

// Background gradient anchors
val NavyDark: Color get() = SkinState.palette.navyDark // fork: skin
val NavyDeep: Color get() = SkinState.palette.navyDeep // fork: skin
val NavyMid: Color get() = SkinState.palette.navyMid // fork: skin

// Surface / Cards
val CardSurface: Color get() = SkinState.palette.cardSurface // fork: skin
val CardSurfaceElevated: Color get() = SkinState.palette.cardSurfaceElevated // fork: skin
val CardBorder: Color get() = SkinState.palette.cardBorder // fork: skin

// Accent colors
val AccentGreen: Color get() = SkinState.palette.accentGreen // fork: skin
val AccentBlue: Color get() = SkinState.palette.accentBlue // fork: skin
val AccentOrange: Color get() = SkinState.palette.accentOrange // fork: skin
val AccentTeal: Color get() = SkinState.palette.accentTeal // fork: skin

// Text
val TextPrimary: Color get() = SkinState.palette.textPrimary // fork: skin
val TextSecondary: Color get() = SkinState.palette.textSecondary // fork: skin
val TextMuted: Color get() = SkinState.palette.textMuted // fork: skin

// SOC traffic light
val SocGreen: Color get() = SkinState.palette.accentGreen // fork: skin — the SOC gauge follows the skin accent (owner)
val SocYellow: Color get() = SkinState.palette.socYellow // fork: skin
val SocRed: Color get() = SkinState.palette.socRed // fork: skin

// Consumption rating
val ConsumptionGood: Color get() = SkinState.palette.socGreen // fork: skin
val ConsumptionMid: Color get() = SkinState.palette.socYellow // fork: skin
val ConsumptionBad: Color get() = SkinState.palette.socRed // fork: skin

// Charts
val ChartGrid: Color get() = SkinState.palette.chartGrid // fork: skin

// Navigation
val NavBarBackground: Color get() = SkinState.palette.navBarBackground // fork: skin
val NavIndicator: Color get() = SkinState.palette.navIndicator // fork: skin
