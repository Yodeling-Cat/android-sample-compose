package uno.lux.mosaic.common.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uno.lux.mosaic.common.R
import uno.lux.mosaic.designsystem.components.rememberDebounced
import uno.lux.mosaic.designsystem.theme.MosaicGradients
import uno.lux.mosaic.designsystem.theme.MosaicTheme

/*
 * Chrome overlaid on media — the badges, play buttons and back affordance that sit on top of
 * thumbnails, galleries and the full-screen viewers. All of it paints over imagery the app can't
 * predict, so each piece scrims the pixels behind it in black and draws its glyphs in white.
 */

/** Behind a badge's text: dark enough to carry white labelMedium over any photo. */
private val BadgeScrim = Color.Black.copy(alpha = 0.42f)

/** Behind a play glyph: lighter, since the thumbnail underneath is the point. */
private val PlayScrim = Color.Black.copy(alpha = 0.34f)

/** Behind an immersive viewer's back arrow, which sits over full-bleed content. */
private val BackButtonScrim = Color.Black.copy(alpha = 0.4f)

/** Behind the composer's remove glyph: opaque enough to read over a bright or a dark frame. */
private val RemoveButtonScrim = Color.Black.copy(alpha = 0.55f)

/** A small dark-scrim pill (white text, optional leading icon) overlaid on a thumbnail corner. */
@Composable
fun MediaBadge(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(BadgeScrim)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

/**
 * The circular play affordance over a video thumbnail. [size] and [iconSize] vary with the
 * thumbnail's own scale — a profile grid cell wears a smaller one than a full-width feed player.
 */
@Composable
fun PlayBadge(
    contentDescription: String,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(PlayScrim),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play_arrow),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The corner affordance that detaches a picked photo or video in the composer. The visible scrim
 * is an *inner* box because [IconButton] expands to the 48dp minimum touch target — painting the
 * background on the button itself would spill a circle well past the thumbnail's corner.
 */
@Composable
fun MediaRemoveButton(
    contentDescription: String,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onRemove, enabled = enabled, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(RemoveButtonScrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The back affordance for the immersive viewers, which hide the system bars and have no app bar
 * to hang a navigation icon on. Inset from the safe-drawing area so it clears the display cutout
 * and the bars once they're swiped back into view.
 */
@Composable
fun OverlayBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onBack.rememberDebounced(),
        modifier = modifier
            .safeDrawingPadding()
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BackButtonScrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.navigate_back),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** All four pieces over a stand-in thumbnail, since each one only makes sense on top of media. */
@Preview
@Composable
private fun MediaOverlaysPreview() {
    MosaicTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MosaicGradients.mediaBrush("preview")),
        ) {
            OverlayBackButton(onBack = {}, modifier = Modifier.align(Alignment.TopStart))
            MediaRemoveButton(
                contentDescription = "Remove",
                enabled = true,
                onRemove = {},
                modifier = Modifier.align(Alignment.TopEnd),
            )
            PlayBadge(
                contentDescription = "Play",
                size = 58.dp,
                iconSize = 32.dp,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            ) {
                MediaBadge(text = "2/5", iconRes = R.drawable.ic_play_arrow)
                MediaBadge(text = "0:42")
            }
        }
    }
}
