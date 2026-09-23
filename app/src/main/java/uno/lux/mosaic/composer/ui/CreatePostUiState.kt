package uno.lux.mosaic.composer.ui

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.NewPostMedia

/** These limits mirror the server's validations; change both sides together. */
const val CREATE_POST_TITLE_MAX_LENGTH = 120
const val CREATE_POST_BODY_MAX_LENGTH = 5000

/** Mirrors the server's `Album::MAX_PHOTOS`. */
const val CREATE_POST_MAX_IMAGES = 10

/** Mirrors the server's `Video::MAX_BYTES`. */
const val CREATE_POST_MAX_VIDEO_BYTES = 25L * 1024 * 1024

@Serializable
@Immutable
sealed interface CreatePostMedia {

    @Serializable
    data object None : CreatePostMedia

    @Serializable
    data class Images(
        val uris: List<String>,
    ) : CreatePostMedia {
        val canAddMore: Boolean
            get() = uris.size < CREATE_POST_MAX_IMAGES
    }

    @Serializable
    data class Video(
        val uri: String,
        val durationSeconds: Int,
    ) : CreatePostMedia
}

/**
 * Text is kept exactly as typed and trimmed in [toNewPost]; trimming on input would fight the
 * cursor.
 */
@Serializable
data class CreatePostForm(
    val title: String = "",
    val body: String = "",
    val media: CreatePostMedia = CreatePostMedia.None,
) {
    val isEmpty: Boolean
        get() = title.isEmpty() && body.isEmpty() && media is CreatePostMedia.None

    val canPublish: Boolean
        get() = title.isNotBlank() && body.isNotBlank()

    fun toNewPost(media: NewPostMedia) =
        NewPost(title = title.trim(), body = body.trim(), media = media)
}

@Immutable
sealed interface CreatePostError {

    data class Failed(
        val error: AppError,
    ) : CreatePostError

    data object VideoTooLarge : CreatePostError
}

data class CreatePostUiState(
    val form: CreatePostForm = CreatePostForm(),
    val isPublishing: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val error: CreatePostError? = null,
)
