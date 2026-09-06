package uno.lux.mosaic.app.theme

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
    MosaicColors(textTertiary = MosaicLightText3, like = MosaicLike)
}

internal fun mosaicColors(darkTheme: Boolean) = MosaicColors(
    textTertiary = if (darkTheme) MosaicDarkText3 else MosaicLightText3,
    like = MosaicLike,
)
