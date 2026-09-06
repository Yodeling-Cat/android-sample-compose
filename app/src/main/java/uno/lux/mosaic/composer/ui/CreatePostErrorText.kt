package uno.lux.mosaic.composer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import uno.lux.mosaic.R
import uno.lux.mosaic.common.asText

/** Maps a [CreatePostError] to its localized message, filling in any limit it names. */
@Composable
fun CreatePostError.asText(): String = when (this) {
    is CreatePostError.Failed -> error.asText()

    CreatePostError.VideoTooLarge -> stringResource(
        R.string.create_post_video_too_large,
        CREATE_POST_MAX_VIDEO_BYTES / (1024 * 1024),
    )
}
