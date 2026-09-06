package uno.lux.mosaic.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Indigo accent for selected/active states; `secondaryContainer` is the soft accent used by
// the nav indicator and segmented buttons. `surfaceTint` is transparent so elevated surfaces
// keep their exact Mosaic color while still casting the soft card shadow.

private val LightColors = lightColorScheme(
    primary = MosaicAccent,
    onPrimary = MosaicOnAccent,
    primaryContainer = MosaicLightAccentSoft,
    onPrimaryContainer = MosaicAccent,
    secondary = MosaicAccent,
    onSecondary = MosaicOnAccent,
    secondaryContainer = MosaicLightAccentSoft,
    onSecondaryContainer = MosaicAccent,
    tertiary = MosaicAccent,
    onTertiary = MosaicOnAccent,
    tertiaryContainer = MosaicLightAccentSoft,
    onTertiaryContainer = MosaicAccent,
    background = MosaicLightBg,
    onBackground = MosaicLightText,
    surface = MosaicLightSurface,
    onSurface = MosaicLightText,
    surfaceVariant = MosaicLightBorder,
    onSurfaceVariant = MosaicLightText2,
    surfaceTint = Color.Transparent,
    surfaceBright = MosaicLightSurface,
    surfaceDim = MosaicLightBg,
    surfaceContainerLowest = MosaicLightSurface,
    surfaceContainerLow = MosaicLightSurface,
    surfaceContainer = MosaicLightSurface,
    surfaceContainerHigh = MosaicLightSurface,
    surfaceContainerHighest = MosaicLightSurface,
    outline = MosaicLightBorderStrong,
    outlineVariant = MosaicLightBorder,
    error = MosaicLightDanger,
    onError = MosaicOnAccent,
    inverseSurface = MosaicLightText,
    inverseOnSurface = MosaicLightSurface,
    scrim = Color(0xFF000000),
)

private val DarkColors = darkColorScheme(
    primary = MosaicAccent,
    onPrimary = MosaicOnAccent,
    primaryContainer = MosaicDarkAccentSoft,
    onPrimaryContainer = MosaicAccent,
    secondary = MosaicAccent,
    onSecondary = MosaicOnAccent,
    secondaryContainer = MosaicDarkAccentSoft,
    onSecondaryContainer = MosaicAccent,
    tertiary = MosaicAccent,
    onTertiary = MosaicOnAccent,
    tertiaryContainer = MosaicDarkAccentSoft,
    onTertiaryContainer = MosaicAccent,
    background = MosaicDarkBg,
    onBackground = MosaicDarkText,
    surface = MosaicDarkSurface,
    onSurface = MosaicDarkText,
    surfaceVariant = MosaicDarkBorder,
    onSurfaceVariant = MosaicDarkText2,
    surfaceTint = Color.Transparent,
    surfaceBright = MosaicDarkSurface,
    surfaceDim = MosaicDarkBg,
    surfaceContainerLowest = MosaicDarkSurface,
    surfaceContainerLow = MosaicDarkSurface,
    surfaceContainer = MosaicDarkSurface,
    surfaceContainerHigh = MosaicDarkSurface,
    surfaceContainerHighest = MosaicDarkSurface,
    outline = MosaicDarkBorderStrong,
    outlineVariant = MosaicDarkBorder,
    error = MosaicDarkDanger,
    onError = MosaicOnAccent,
    inverseSurface = MosaicDarkText,
    inverseOnSurface = MosaicDarkSurface,
    scrim = Color(0xFF000000),
)

@Composable
fun MosaicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMosaicColors provides mosaicColors(darkTheme)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
