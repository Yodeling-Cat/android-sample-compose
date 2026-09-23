package uno.lux.mosaic.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Draws the status bar's icons light while shown, and restores them on dispose.
 *
 * `MainActivity` picks the icons from the theme, which is wrong for a page that paints something
 * dark behind the status bar whatever the theme, such as an accent app bar in light mode.
 *
 * Restoring once on dispose is enough: only a theme flip overwrites the icons, and Settings, where
 * the theme is flipped, pushes over this page and disposes the effect.
 */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    // Null in a @Preview, where the whole effect then correctly does nothing.
    val window = LocalContext.current.findActivity()?.window

    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false

        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
}
