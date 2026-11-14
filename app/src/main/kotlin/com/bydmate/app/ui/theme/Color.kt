package com.bydmate.app.ui.theme

import androidx.compose.ui.graphics.Color

// Every token reads the active skin (Skins.kt). The names stay the author's so the
// screens compile unchanged; reading one inside a composable subscribes it to skin changes.

// Background gradient anchors
val NavyDark: Color get() = SkinState.palette.navyDark
val NavyDeep: Color get() = SkinState.palette.navyDeep
val NavyMid: Color get() = SkinState.palette.navyMid

// Surface / Cards
val CardSurface: Color get() = SkinState.palette.cardSurface
val CardSurfaceElevated: Color get() = SkinState.palette.cardSurfaceElevated
val CardBorder: Color get() = SkinState.palette.cardBorder

// Accent colors
val AccentGreen: Color get() = SkinState.palette.accentGreen
val AccentBlue: Color get() = SkinState.palette.accentBlue
val AccentOrange: Color get() = SkinState.palette.accentOrange
val AccentTeal: Color get() = SkinState.palette.accentTeal

// Text
val TextPrimary: Color get() = SkinState.palette.textPrimary
val TextSecondary: Color get() = SkinState.palette.textSecondary
val TextMuted: Color get() = SkinState.palette.textMuted

// SOC traffic light
val SocGreen: Color get() = SkinState.palette.socGreen
val SocYellow: Color get() = SkinState.palette.socYellow
val SocRed: Color get() = SkinState.palette.socRed

// Consumption rating
val ConsumptionGood: Color get() = SkinState.palette.socGreen
val ConsumptionMid: Color get() = SkinState.palette.socYellow
val ConsumptionBad: Color get() = SkinState.palette.socRed

// Charts
val ChartGrid: Color get() = SkinState.palette.chartGrid

// Navigation
val NavBarBackground: Color get() = SkinState.palette.navBarBackground
val NavIndicator: Color get() = SkinState.palette.navIndicator
