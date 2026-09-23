package uno.lux.mosaic.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uno.lux.mosaic.designsystem.R
import uno.lux.mosaic.designsystem.theme.MosaicGradients
import uno.lux.mosaic.designsystem.theme.MosaicTheme

private val ButtonScrim = Color.Black.copy(alpha = 0.32f)

@Composable
fun ScrimIconButton(
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

@Preview
@Composable
private fun ScrimIconButtonPreview() {
    MosaicTheme {
        Row(Modifier.background(MosaicGradients.mediaBrush("preview"))) {
            listOf(0f, 0.5f, 1f).forEach { progress ->
                Box(Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = progress))) {
                    ScrimIconButton(
                        iconRes = R.drawable.ic_app,
                        contentDescription = stringResource(R.string.app_name),
                        progress = progress,
                        onClick = {},
                    )
                }
            }
        }
    }
}
