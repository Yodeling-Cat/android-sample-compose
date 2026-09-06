package uno.lux.mosaic.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

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
