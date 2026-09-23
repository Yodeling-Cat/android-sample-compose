package uno.lux.mosaic.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MosaicColors(
    val textTertiary: Color,
    val like: Color,
)

val LocalMosaicColors = staticCompositionLocalOf {
    mosaicColors(darkTheme = false)
}

internal fun mosaicColors(darkTheme: Boolean) =
    MosaicColors(
        textTertiary = if (darkTheme) MosaicDarkText3 else MosaicLightText3,
        like = MosaicLike,
    )

/** Pair with `LightStatusBarIcons()`: the bar reaches behind the status bar. */
@Composable
fun accentBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)
