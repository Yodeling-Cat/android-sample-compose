package uno.lux.mosaic.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val MosaicAccentBrush: Brush = Brush.horizontalGradient(listOf(MosaicAccent, MosaicAccentDeep))

/** Draws beneath everything, so the page needs a transparent app bar and scaffold container. */
@Composable
fun rememberAccentWash(height: Dp = AccentWashHeight): Brush {
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    return remember(accent, density, height) {
        Brush.verticalGradient(
            colors = listOf(accent.copy(alpha = ACCENT_WASH_ALPHA), Color.Transparent),
            endY = with(density) { height.toPx() },
        )
    }
}

private val AccentWashHeight = 220.dp

private const val ACCENT_WASH_ALPHA = 0.08f
