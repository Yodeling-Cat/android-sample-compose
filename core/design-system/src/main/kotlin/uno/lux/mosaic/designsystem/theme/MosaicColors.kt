package uno.lux.mosaic.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Mosaic tokens that don't map cleanly onto a Material color role:
 * [textTertiary] is the most muted text (bylines, the end-of-feed marker), and [like] is the
 * coral like-state color. Provided through [LocalMosaicColors] by `MosaicTheme`.
 */
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

/**
 * The accent app bar's colors: brand indigo behind a light title, navigation icon and actions.
 *
 * Named once because `TopAppBarColors` has three content slots, and a slot left at its default
 * shows up only when a bar first uses it, as a dark icon on indigo.
 *
 * The bar reaches behind the status bar, so the screen also owes it `LightStatusBarIcons()`.
 */
@Composable
fun accentBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)
