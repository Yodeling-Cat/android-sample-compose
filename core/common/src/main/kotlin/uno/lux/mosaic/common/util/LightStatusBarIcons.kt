package uno.lux.mosaic.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Restores on dispose only: nothing but a theme flip overwrites the icons, and flipping it pushes
 * Settings over this page.
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
