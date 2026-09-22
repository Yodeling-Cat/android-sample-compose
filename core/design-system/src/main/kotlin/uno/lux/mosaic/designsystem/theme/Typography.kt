@file:OptIn(ExperimentalTextApi::class)

package uno.lux.mosaic.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import uno.lux.mosaic.designsystem.R

// Variable fonts: one TTF per family, the weight axis selected per FontWeight.
private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun bricolage(weight: Int) = Font(
    resId = R.font.bricolage_grotesque,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Manrope — UI chrome and body text: clean and legible. */
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

/** Bricolage Grotesque — brand wordmark and post titles: editorial character. */
val Bricolage = FontFamily(bricolage(400), bricolage(600), bricolage(700))

private val Defaults = Typography()

val Typography = Typography(
    // Display & headline — Bricolage.
    displayLarge = Defaults.displayLarge.copy(fontFamily = Bricolage),
    displayMedium = Defaults.displayMedium.copy(fontFamily = Bricolage),
    displaySmall = Defaults.displaySmall.copy(fontFamily = Bricolage),
    headlineLarge = Defaults.headlineLarge.copy(fontFamily = Bricolage),
    headlineMedium = Defaults.headlineMedium.copy(fontFamily = Bricolage),
    headlineSmall = Defaults.headlineSmall.copy(fontFamily = Bricolage),
    // Titles — Bricolage; titleMedium pinned to the Mosaic post-title style.
    titleLarge = Defaults.titleLarge.copy(fontFamily = Bricolage, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(
        fontFamily = Bricolage,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.015).em,
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.01).em,
    ),
    // Body & labels — Manrope.
    bodyLarge = Defaults.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = Defaults.labelMedium.copy(fontFamily = Manrope),
    labelSmall = Defaults.labelSmall.copy(fontFamily = Manrope),
)
