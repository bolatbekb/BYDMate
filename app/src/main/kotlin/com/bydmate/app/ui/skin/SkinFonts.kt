package com.bydmate.app.ui.skin

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.bydmate.app.R
import com.bydmate.app.ui.home.graphite.GrRajdhani

private val Weights = intArrayOf(300, 400, 500, 600, 700)

/** A variable font at the weights the app uses, the way the Graphite home loads Inter. */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int): FontFamily = FontFamily(
    Weights.map { w ->
        Font(res, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private val Inter: FontFamily by lazy { variable(R.font.gr_inter) }
private val Exo2: FontFamily by lazy { variable(R.font.skin_exo2) }
private val Manrope: FontFamily by lazy { variable(R.font.skin_manrope) }
private val RobotoCondensed: FontFamily by lazy { variable(R.font.skin_roboto_condensed) }

/** The family of a skin font; SYSTEM gives null, which leaves the author's typography untouched. */
fun SkinFont.family(): FontFamily? = when (this) {
    SkinFont.SYSTEM -> null
    SkinFont.INTER -> Inter
    SkinFont.EXO2 -> Exo2
    SkinFont.MANROPE -> Manrope
    SkinFont.ROBOTO_CONDENSED -> RobotoCondensed
    SkinFont.RAJDHANI -> GrRajdhani
}

/** [base] with [family] on all 15 styles, sizes untouched; a null family returns [base] itself. */
fun skinTypography(base: Typography, family: FontFamily?): Typography {
    if (family == null) return base
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}
