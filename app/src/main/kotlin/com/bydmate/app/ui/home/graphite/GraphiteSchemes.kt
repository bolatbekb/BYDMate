package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.graphics.Color

/*
 * Four more dark Graphite schemes chosen by the owner: Mocha (warm coffee), Hub (in the spirit of
 * GitHub Dark), One (in the spirit of Atom One Dark) and Code (in the spirit of VS Code Dark
 * Modern). Same 33 tokens as the mockup palettes; the data colours keep their meaning:
 * electric = battery and kWh, fuel = petrol and the engine, cold/heat = temperatures only.
 */

val GrMocha = GrPalette(
    bg = Color(0xFF1E1A16), bgGlow = Color(0xFF262019),
    cardTop = Color(0xFF2A241F), cardBottom = Color(0xFF241F1A),
    cardEdge = Color(0x0FFFF0DC), cardEdgeTop = Color(0x1AFFF0DC),
    cardInnerGlow = Color(0x00000000), cardOuterGlow = Color(0x8C000000),
    divider = Color(0x14F2E9DC), track = Color(0x1AF2E9DC),
    chip = Color(0xFF2A241F), chipEdge = Color(0x14F2E9DC),
    inset = Color(0xFF1A1612), insetEdge = Color(0x99000000),
    tabActive = Color(0xFF3A3129), tabActiveEdge = Color(0x1AF2E9DC),
    textPrimary = Color(0xFFF2E9DC), textSecondary = Color(0xFFB8AA96), textTertiary = Color(0xFF7E7262),
    electric = Color(0xFF7BC47F), fuel = Color(0xFFE0A458), fuelDeep = Color(0xFFC98A3C),
    heat = Color(0xFFE07A5F), cold = Color(0xFF8FB3D9), coldDeep = Color(0xFF5F86B0),
    progressStart = Color(0xFFB8AA96), progressEnd = Color(0xFFF2E9DC),
    playTop = Color(0xFFF6EEE2), playBottom = Color(0xFFD9CBB6),
    photoTint = Color(0xFFE8D7C0), photoShade = Color(0xB81E1A16), photoShadeBottom = Color(0xE01E1A16),
    spotify = Color(0xFF1ED760),
    look = PhotoLook(0.85f, 0.95f, 1.04f), edgeStyle = false, dimOpacity = 0.38f,
)

val GrHub = GrPalette(
    bg = Color(0xFF0D1117), bgGlow = Color(0xFF111723),
    cardTop = Color(0xFF161B22), cardBottom = Color(0xFF12171E),
    cardEdge = Color(0xFF30363D), cardEdgeTop = Color(0x1AFFFFFF),
    cardInnerGlow = Color(0x00000000), cardOuterGlow = Color(0xB3000000),
    divider = Color(0xFF21262D), track = Color(0xFF21262D),
    chip = Color(0xFF161B22), chipEdge = Color(0xFF30363D),
    inset = Color(0xFF0D1117), insetEdge = Color(0xFF30363D),
    tabActive = Color(0xFF21262D), tabActiveEdge = Color(0xFF3D444D),
    textPrimary = Color(0xFFE6EDF3), textSecondary = Color(0xFF8D96A0), textTertiary = Color(0xFF6E7681),
    electric = Color(0xFF3FB950), fuel = Color(0xFFD29922), fuelDeep = Color(0xFFBB8009),
    heat = Color(0xFFF85149), cold = Color(0xFF58A6FF), coldDeep = Color(0xFF1F6FEB),
    progressStart = Color(0xFF1F6FEB), progressEnd = Color(0xFF58A6FF),
    playTop = Color(0xFFF0F6FC), playBottom = Color(0xFFC9D1D9),
    photoTint = Color(0xFFB8C7DB), photoShade = Color(0xA80D1117), photoShadeBottom = Color(0xE60D1117),
    spotify = Color(0xFF1ED760),
    look = PhotoLook(0.8f, 0.85f, 1.06f), edgeStyle = false, dimOpacity = 0.35f,
)

val GrOne = GrPalette(
    bg = Color(0xFF21252B), bgGlow = Color(0xFF262A31),
    cardTop = Color(0xFF282C34), cardBottom = Color(0xFF24282F),
    cardEdge = Color(0x0FFFFFFF), cardEdgeTop = Color(0x14FFFFFF),
    cardInnerGlow = Color(0x00000000), cardOuterGlow = Color(0x8C000000),
    divider = Color(0x14ABB2BF), track = Color(0x1FABB2BF),
    chip = Color(0xFF2C313A), chipEdge = Color(0x14ABB2BF),
    inset = Color(0xFF1E2227), insetEdge = Color(0x80000000),
    tabActive = Color(0xFF3A3F4B), tabActiveEdge = Color(0x33C678DD),
    textPrimary = Color(0xFFE6E9EF), textSecondary = Color(0xFFABB2BF), textTertiary = Color(0xFF5C6370),
    electric = Color(0xFF98C379), fuel = Color(0xFFE5C07B), fuelDeep = Color(0xFFD19A66),
    heat = Color(0xFFE06C75), cold = Color(0xFF61AFEF), coldDeep = Color(0xFF528BFF),
    progressStart = Color(0xFFC678DD), progressEnd = Color(0xFF61AFEF),
    playTop = Color(0xFFE6E9EF), playBottom = Color(0xFFABB2BF),
    photoTint = Color(0xFFC9CEDA), photoShade = Color(0xB821252B), photoShadeBottom = Color(0xE021252B),
    spotify = Color(0xFF1ED760),
    look = PhotoLook(0.85f, 0.9f, 1.05f), edgeStyle = false, dimOpacity = 0.38f,
)

val GrCode = GrPalette(
    bg = Color(0xFF181818), bgGlow = Color(0xFF1F1F1F),
    cardTop = Color(0xFF252526), cardBottom = Color(0xFF202021),
    cardEdge = Color(0xFF2B2B2B), cardEdgeTop = Color(0x12FFFFFF),
    cardInnerGlow = Color(0x00000000), cardOuterGlow = Color(0x99000000),
    divider = Color(0xFF2B2B2B), track = Color(0xFF333333),
    chip = Color(0xFF252526), chipEdge = Color(0xFF2B2B2B),
    inset = Color(0xFF1F1F1F), insetEdge = Color(0xFF2B2B2B),
    tabActive = Color(0xFF37373D), tabActiveEdge = Color(0xFF0078D4),
    textPrimary = Color(0xFFCCCCCC), textSecondary = Color(0xFF9D9D9D), textTertiary = Color(0xFF6E7681),
    electric = Color(0xFF4EC9B0), fuel = Color(0xFFDCDCAA), fuelDeep = Color(0xFFCE9178),
    heat = Color(0xFFF14C4C), cold = Color(0xFF4FC1FF), coldDeep = Color(0xFF0078D4),
    progressStart = Color(0xFF0078D4), progressEnd = Color(0xFF4FC1FF),
    playTop = Color(0xFFE8E8E8), playBottom = Color(0xFFBDBDBD),
    photoTint = Color(0xFFC8CCD2), photoShade = Color(0xB8181818), photoShadeBottom = Color(0xE0181818),
    spotify = Color(0xFF1ED760),
    look = PhotoLook(0.82f, 0.9f, 1.06f), edgeStyle = false, dimOpacity = 0.35f,
)
