package uno.lux.mosaic.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The brand gradient, indigo running into violet. It fills a page's one primary action, so the
 * control the page exists for is the one thing on it wearing full-strength colour. Kept as a
 * single value rather than a per-call brush, since it depends on neither theme nor density —
 * both ends are brand colours, and the brand does not change in the dark.
 */
val MosaicAccentBrush: Brush = Brush.horizontalGradient(listOf(MosaicAccent, MosaicAccentDeep))

/**
 * A vertical wash of the accent at the top of a page, faded out entirely by [height] down it.
 * It is what a form-shaped screen wears instead of the imagery a feed carries: enough colour to
 * tell the eye where the page starts, not enough to compete with a field label.
 *
 * It draws beneath everything, so a page wearing it needs a transparent app bar *and* a
 * transparent scaffold container — either one left opaque paints straight over it.
 *
 * Remembered per accent and density, because a `Brush` built inline would be re-allocated on
 * every recomposition of the page that wears it.
 */
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

/** How far down the page the wash runs before it has faded to nothing. */
private val AccentWashHeight = 220.dp

/** The accent's opacity at the very top of the window, where the wash is strongest. */
private const val ACCENT_WASH_ALPHA = 0.08f
