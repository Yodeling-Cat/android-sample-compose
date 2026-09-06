package uno.lux.mosaic.app.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Mosaic's duotone gradient stand-ins for imagery, keyed deterministically by a string so a
 * given user / album / video always renders the same hues — no image-loading dependency.
 *
 * The brushes are built once and reused, so avatars and grid cells
 * never re-allocate one while recomposing.They provide a rich set used for cover
 * photos and media thumbnails.
 */
object MosaicGradients {
    private val palette = listOf(
        Color(0xFF5B6CFF) to Color(0xFF9B4DFF),
        Color(0xFFFF7A59) to Color(0xFFFF4D8D),
        Color(0xFF1FB6A6) to Color(0xFF0E8F9C),
        Color(0xFFFFB547) to Color(0xFFFF7A59),
        Color(0xFF7C5CFF) to Color(0xFF4D8DFF),
        Color(0xFFE0407A) to Color(0xFFA83B8F),
        Color(0xFF2DD4BF) to Color(0xFF3B82F6),
        Color(0xFFF59E0B) to Color(0xFFEF4444),
    )

    private val brushes = palette.map { (start, end) -> Brush.linearGradient(listOf(start, end)) }

    private const val AVATAR_PALETTE_SIZE = 6

    /** A brush for an avatar, keyed by [id] over the avatar hue set. */
    fun avatarBrush(id: String): Brush = brushes[id.hashCode().mod(brushes.size)]

    /** A brush for cover / media imagery, keyed by [key] over the full hue set. */
    fun mediaBrush(key: String): Brush = brushes[key.hashCode().mod(brushes.size)]
}
