package com.bydmate.app.ui.home.graphite

import androidx.compose.ui.graphics.Color
import com.bydmate.app.ui.theme.SkinPalette

/** Photo colour grading applied to the car photo. */
data class PhotoLook(val saturation: Float, val brightness: Float, val contrast: Float)

/** Home palette: 33 colours 1:1 from the owner's design (tools/graphite/design/CarPalettes.kt) plus variant fields. */
data class GrPalette(
    val bg: Color,
    val bgGlow: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val cardEdge: Color,
    val cardEdgeTop: Color,
    val cardInnerGlow: Color,
    val cardOuterGlow: Color,
    val divider: Color,
    val track: Color,
    val chip: Color,
    val chipEdge: Color,
    val inset: Color,
    val insetEdge: Color,
    val tabActive: Color,
    val tabActiveEdge: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val electric: Color,
    val fuel: Color,
    val fuelDeep: Color,
    val heat: Color,
    val cold: Color,
    val coldDeep: Color,
    val progressStart: Color,
    val progressEnd: Color,
    val playTop: Color,
    val playBottom: Color,
    val photoTint: Color,
    val photoShade: Color,
    val photoShadeBottom: Color,
    val spotify: Color,
    val look: PhotoLook,
    val edgeStyle: Boolean,
    val dimOpacity: Float,
)

val GrSoft = GrPalette(

    bg = Color(0xFF121316), // Фон экрана
    bgGlow = Color(0xFF121316), // Центральное свечение фона (radial 1600×900, центр 50%/40%)
    cardTop = Color(0xFF1E2024), // Карточка — верх градиента
    cardBottom = Color(0xFF191B1E), // Карточка — низ градиента
    cardEdge = Color(0x06FFFFFF), // Контур карточки 1 px
    cardEdgeTop = Color(0x12FFFFFF), // Блик верхней кромки 1 px
    cardInnerGlow = Color(0x00000000), // Внутреннее свечение (blur 40)
    cardOuterGlow = Color(0x8C000000), // Внешняя тень/свечение
    divider = Color(0x12FFFFFF), // Разделители, сетка графика
    track = Color(0x14FFFFFF), // Пустая часть шкал/слайдеров
    chip = Color(0xFF1D1F23), // Фон плашек верхней строки
    chipEdge = Color(0x0FFFFFFF), // Контур плашек
    inset = Color(0xFF151619), // Утопленные блоки (громкость, табы, футер)
    insetEdge = Color(0x99000000), // Контур утопленных блоков
    tabActive = Color(0xFF2A2D32), // Активная вкладка / кнопка меню
    tabActiveEdge = Color(0x14FFFFFF), // Контур активной вкладки
    textPrimary = Color(0xFFF3F4F6), // Основной текст, цифры
    textSecondary = Color(0xFFA7ABB3), // Подписи
    textTertiary = Color(0xFF6D727B), // Второстепенное, оси
    electric = Color(0xFF5AD6A4), // Электро: батарея, кВт·ч, активная точка меню
    fuel = Color(0xFFF2B45C), // Бензин, ДВС
    fuelDeep = Color(0xFFE09A3C), // Конец градиента бензина
    heat = Color(0xFFF08A64), // Подогрев, тепло
    cold = Color(0xFF7AB2FF), // Холод, наружная температура
    coldDeep = Color(0xFF4F7FD6), // Начало шкалы холода
    progressStart = Color(0xFFC9CCD1), // Прогресс трека — начало
    progressEnd = Color(0xFFF3F4F6), // Прогресс трека — конец
    playTop = Color(0xFFF6F7F8), // Кнопка Play — верх
    playBottom = Color(0xFFD4D7DC), // Кнопка Play — низ
    photoTint = Color(0xFFECEEF2), // Фото: тонирование (multiply)
    photoShade = Color(0xB8121316), // Фото: затемнение сверху/виньетка
    photoShadeBottom = Color(0xE0121316), // Фото: затемнение снизу
    spotify = Color(0xFF1ED760), // Значок источника
    look = PhotoLook(0.9f, 1.0f, 1.03f),
    edgeStyle = false,
    dimOpacity = 0.42f,
)

val GrDark = GrPalette(

    bg = Color(0xFF03050A), // Фон экрана
    bgGlow = Color(0xFF08101C), // Центральное свечение фона (radial 1600×900, центр 50%/40%)
    cardTop = Color(0xFF0B1019), // Карточка — верх градиента
    cardBottom = Color(0xFF070A11), // Карточка — низ градиента
    cardEdge = Color(0x0F78A0DC), // Контур карточки 1 px
    cardEdgeTop = Color(0x1A96BEFF), // Блик верхней кромки 1 px
    cardInnerGlow = Color(0x00000000), // Внутреннее свечение (blur 40)
    cardOuterGlow = Color(0xCC000000), // Внешняя тень/свечение
    divider = Color(0x178CAAD2), // Разделители, сетка графика
    track = Color(0x1C8CAAD2), // Пустая часть шкал/слайдеров
    chip = Color(0xFF0A0F17), // Фон плашек верхней строки
    chipEdge = Color(0x0FFFFFFF), // Контур плашек
    inset = Color(0xFF05080D), // Утопленные блоки (громкость, табы, футер)
    insetEdge = Color(0x99000000), // Контур утопленных блоков
    tabActive = Color(0xFF16202E), // Активная вкладка / кнопка меню
    tabActiveEdge = Color(0x14FFFFFF), // Контур активной вкладки
    textPrimary = Color(0xFFEEF3FA), // Основной текст, цифры
    textSecondary = Color(0xFF8E9BB0), // Подписи
    textTertiary = Color(0xFF56627A), // Второстепенное, оси
    electric = Color(0xFF22E06A), // Электро: батарея, кВт·ч, активная точка меню
    fuel = Color(0xFFFF8A1F), // Бензин, ДВС
    fuelDeep = Color(0xFFFF6A1A), // Конец градиента бензина
    heat = Color(0xFFFF4D3A), // Подогрев, тепло
    cold = Color(0xFF5AA2FF), // Холод, наружная температура
    coldDeep = Color(0xFF2F62C8), // Начало шкалы холода
    progressStart = Color(0xFF3C78D8), // Прогресс трека — начало
    progressEnd = Color(0xFF9CC4FF), // Прогресс трека — конец
    playTop = Color(0xFFF2F6FB), // Кнопка Play — верх
    playBottom = Color(0xFFC9D3E0), // Кнопка Play — низ
    photoTint = Color(0xFFA7BDE0), // Фото: тонирование (multiply)
    photoShade = Color(0xA803050A), // Фото: затемнение сверху/виньетка
    photoShadeBottom = Color(0xE603050A), // Фото: затемнение снизу
    spotify = Color(0xFF1ED760), // Значок источника
    look = PhotoLook(0.8f, 0.82f, 1.08f),
    edgeStyle = false,
    dimOpacity = 0.35f,
)

val GrEdge = GrPalette(

    bg = Color(0xFF03050A), // Фон экрана
    bgGlow = Color(0xFF03050A), // Центральное свечение фона (radial 1600×900, центр 50%/40%)
    cardTop = Color(0xFF04070C), // Карточка — верх градиента
    cardBottom = Color(0xFF03050A), // Карточка — низ градиента
    cardEdge = Color(0x335A96EB), // Контур карточки 1 px
    cardEdgeTop = Color(0x52A0CDFF), // Блик верхней кромки 1 px
    cardInnerGlow = Color(0x0D326EDC), // Внутреннее свечение (blur 40)
    cardOuterGlow = Color(0x12326EDC), // Внешняя тень/свечение
    divider = Color(0x178CAAD2), // Разделители, сетка графика
    track = Color(0x1C8CAAD2), // Пустая часть шкал/слайдеров
    chip = Color(0x00000000), // Фон плашек верхней строки
    chipEdge = Color(0x2E5A96EB), // Контур плашек
    inset = Color(0x00000000), // Утопленные блоки (громкость, табы, футер)
    insetEdge = Color(0x245A96EB), // Контур утопленных блоков
    tabActive = Color(0x1F3C78DC), // Активная вкладка / кнопка меню
    tabActiveEdge = Color(0x596EAAFF), // Контур активной вкладки
    textPrimary = Color(0xFFEEF3FA), // Основной текст, цифры
    textSecondary = Color(0xFF8E9BB0), // Подписи
    textTertiary = Color(0xFF56627A), // Второстепенное, оси
    electric = Color(0xFF22E06A), // Электро: батарея, кВт·ч, активная точка меню
    fuel = Color(0xFFFF8A1F), // Бензин, ДВС
    fuelDeep = Color(0xFFFF6A1A), // Конец градиента бензина
    heat = Color(0xFFFF4D3A), // Подогрев, тепло
    cold = Color(0xFF5AA2FF), // Холод, наружная температура
    coldDeep = Color(0xFF2F62C8), // Начало шкалы холода
    progressStart = Color(0xFF3C78D8), // Прогресс трека — начало
    progressEnd = Color(0xFF9CC4FF), // Прогресс трека — конец
    playTop = Color(0xFFF2F6FB), // Кнопка Play — верх
    playBottom = Color(0xFFC9D3E0), // Кнопка Play — низ
    photoTint = Color(0xFF86A2D0), // Фото: тонирование (multiply)
    photoShade = Color(0xBD03050A), // Фото: затемнение сверху/виньетка
    photoShadeBottom = Color(0xEB03050A), // Фото: затемнение снизу
    spotify = Color(0xFF1ED760), // Значок источника
    look = PhotoLook(0.7f, 0.74f, 1.12f),
    edgeStyle = true,
    dimOpacity = 0.35f,
)

/** Home palette for the active skin id; any non-Graphite skin falls back to Dark. */
fun homePalette(skinId: String): GrPalette = when (skinId) {
    "graphite_soft" -> GrSoft
    "graphite_edge" -> GrEdge
    else -> GrDark
}

/** App-wide skin palette derived from a home palette. */
fun GrPalette.toSkinPalette(): SkinPalette = SkinPalette(
    navyDark = bg,
    navyDeep = bgGlow,
    navyMid = cardBottom,
    cardSurface = cardTop,
    cardSurfaceElevated = tabActive,
    cardBorder = cardEdge,
    accentGreen = electric,
    accentOrange = fuel,
    accentBlue = cold,
    accentTeal = progressEnd,
    textPrimary = textPrimary,
    textSecondary = textSecondary,
    textMuted = textTertiary,
    chartGrid = divider,
    navBarBackground = if (edgeStyle) cardTop else inset,
)
