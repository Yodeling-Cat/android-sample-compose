package uno.lux.mosaic.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import uno.lux.mosaic.app.util.rememberDebounced

/** Behind the glyph while the bar is transparent: dark enough to carry a white icon over any cover. */
private val ButtonScrim = Color.Black.copy(alpha = 0.32f)

/**
 * An app-bar icon button for a bar that starts transparent over full-bleed content and fills to
 * the surface color on scroll. Its gray circular scrim (and white tint) fade to a bare on-surface
 * icon as [progress] goes 0 → 1, so the button tracks whatever drives the bar's own fill.
 */
@Composable
internal fun ScrimIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick.rememberDebounced(), modifier = modifier) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(ButtonScrim.copy(alpha = ButtonScrim.alpha * (1f - progress))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = lerp(Color.White, MaterialTheme.colorScheme.onSurface, progress),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
