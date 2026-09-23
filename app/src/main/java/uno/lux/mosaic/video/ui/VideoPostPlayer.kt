package uno.lux.mosaic.video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import uno.lux.mosaic.R
import uno.lux.mosaic.app.fixtures.SamplePosts
import uno.lux.mosaic.common.formatVideoDuration
import uno.lux.mosaic.common.ui.MediaBadge
import uno.lux.mosaic.common.ui.PlayBadge
import uno.lux.mosaic.designsystem.components.debouncedClickable
import uno.lux.mosaic.designsystem.theme.MosaicGradients
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.video.data.domain.Video

@Composable
internal fun VideoPostPlayer(
    video: Video,
    onOpenFullscreen: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalVideoPlayback.current
    val isActive =
        playback != null && playback.activeVideoUrl == video.videoUrl && playback.player != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // `isActive` already establishes playback != null, so it smart-casts inside this branch.
        if (isActive) {
            VideoSurface(
                // While full screen owns the surface this one detaches, but nothing is released —
                // the controller keeps the instance alive across the transition.
                player = if (playback.isFullscreen) null else playback.player,
                title = video.title,
                isFullscreen = false,
                onFullscreenClick = {
                    // Detach inline first so only the full-screen surface owns the player.
                    playback.enterFullscreen()
                    onOpenFullscreen(video)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            VideoThumbnail(
                video = video,
                onPlay = { playback?.playInline(video.videoUrl) },
            )
        }
    }
}

/** Fitted, not cropped, to match `PlayerView`, so the image does not jump when playback starts. */
@Composable
private fun VideoThumbnail(
    video: Video,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Reset per URL, so a recycled row does not inherit the previous clip's failure.
    var frameFailed by remember(video.thumbnailUrl) { mutableStateOf(false) }
    val hasFrame = video.thumbnailUrl != null && !frameFailed

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (hasFrame) SolidColor(Color.Black) else MosaicGradients.mediaBrush(video.id))
            .debouncedClickable(onClick = onPlay),
        contentAlignment = Alignment.Center,
    ) {
        if (video.thumbnailUrl != null) {
            AsyncImage(
                model = rememberThumbnailRequest(video),
                contentDescription = video.title,
                contentScale = ContentScale.Fit,
                onError = { frameFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        PlayBadge(
            contentDescription = stringResource(R.string.video_play),
            size = 58.dp,
            iconSize = 32.dp,
        )
        MediaBadge(
            text = formatVideoDuration(video.durationSeconds),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
        )
    }
}

@Composable
private fun rememberThumbnailRequest(video: Video): ImageRequest {
    val context = LocalContext.current

    return remember(context, video.thumbnailUrl, video.thumbnailWidth, video.thumbnailHeight) {
        ImageRequest
            .Builder(context)
            .data(video.thumbnailUrl)
            .apply {
                val width = video.thumbnailWidth
                val height = video.thumbnailHeight

                if (width != null && height != null) size(width, height)
            }.build()
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoPostPlayerPreview() {
    MosaicTheme {
        VideoPostPlayer(
            video = SamplePosts.firstNotNullOf { it.video },
            onOpenFullscreen = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
