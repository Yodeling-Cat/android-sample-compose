package uno.lux.mosaic.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Draws the status bar's icons light while shown, restoring whatever they were on dispose.
 *
 * `MainActivity` picks the icon appearance from the resolved theme, which is right for a page that
 * lets its own background reach the status bar. It is wrong for one that paints something dark up
 * there whatever the theme — an accent app bar in light mode would otherwise get dark icons on
 * indigo. Such a page states the exception here rather than in the Activity, which cannot know
 * what the top of the current page is painted with.
 *
 * The restore is a plain `onDispose` rather than a re-assert on every frame, because the only
 * thing that overwrites the appearance is `MainActivity` reacting to a theme flip, and the theme
 * can only be flipped from Settings — which pushes over this page and disposes the effect anyway.
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
