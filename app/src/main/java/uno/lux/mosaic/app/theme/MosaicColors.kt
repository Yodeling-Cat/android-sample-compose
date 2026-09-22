package uno.lux.mosaic.app.theme

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
 * The accent-filled app bar: brand indigo behind a title, a navigation icon and any actions, all
 * three of them on the light side of it.
 *
 * Named here rather than spelled out per screen because `TopAppBarColors` carries the container
 * and *three separate* content slots, and a slot left at its default is invisible until the day a
 * bar happens to use it — an accent bar that never set `actionIconContentColor` reads perfectly
 * well right up to the moment it grows its first action, and then draws it in `onSurface` dark.
 *
 * A bar wearing this reaches up behind the status bar, so the screen also owes it
 * `LightStatusBarIcons()`: the Activity picks the icon appearance from the resolved theme,
 * which is the wrong answer over indigo in light mode.
 */
@Composable
fun accentBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primary,
    titleContentColor = MaterialTheme.colorScheme.onPrimary,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
)
