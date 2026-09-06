package uno.lux.mosaic.app.theme

import androidx.compose.ui.unit.dp

/**
 * Mosaic elevation tokens that don't map onto a Material role.
 */
object MosaicElevations {
    /** Shadow cast by a filled/scrolled app bar and any header pinned flush beneath it. */
    val ScrolledBar = 4.dp

    /** Lift under a grouped block of controls, enough to part it from the page tint behind. */
    val Card = 0.dp
}
