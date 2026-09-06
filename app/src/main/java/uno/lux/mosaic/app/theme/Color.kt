package uno.lux.mosaic.app.theme

import androidx.compose.ui.graphics.Color

// Mosaic design tokens (claude.ai/design handoff). Warm-neutral surfaces, indigo accent,
// coral like-state, with full light/dark sets.

// Brand — shared across themes
val MosaicAccent = Color(0xFF3F5BD8) // indigo
val MosaicAccentDeep = Color(0xFF7A45D6) // violet — the far end of the brand gradient
val MosaicLike = Color(0xFFEF4D6B) // coral like-state
val MosaicOnAccent = Color(0xFFFFFFFF)

// Scrims drawn behind the gesture navigation bar in edge-to-edge, per the AndroidX guidance.
val MosaicLightNavBarScrim = Color(0xE6FFFFFF)
val MosaicDarkNavBarScrim = Color(0x801B1B1B)

// Light
val MosaicLightBg = Color(0xFFF3F1EC) // warm paper, so white cards read as lifted off it
val MosaicLightSurface = Color(0xFFFFFFFF)
val MosaicLightBorder = Color(0xFFECEDF1)
val MosaicLightBorderStrong = Color(0xFFD2D5DD)
val MosaicLightText = Color(0xFF161820)
val MosaicLightText2 = Color(0xFF565B66)
val MosaicLightText3 = Color(0xFF8B909C)
val MosaicLightDanger = Color(0xFFE23B4E)
val MosaicLightAccentSoft = Color(0xFFE2E6F9) // accent @ ~15% over surface

// Dark
val MosaicDarkBg = Color(0xFF0C0E12)
val MosaicDarkSurface = Color(0xFF16191F)
val MosaicDarkBorder = Color(0xFF242830)
val MosaicDarkBorderStrong = Color(0xFF353B45)
val MosaicDarkText = Color(0xFFE9EBEF)
val MosaicDarkText2 = Color(0xFFA4AAB5)
val MosaicDarkText3 = Color(0xFF6E7682)
val MosaicDarkDanger = Color(0xFFFF5D6C)
val MosaicDarkAccentSoft = Color(0xFF20294B) // accent @ ~24% over surface
