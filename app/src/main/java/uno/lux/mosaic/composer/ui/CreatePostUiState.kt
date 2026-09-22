package uno.lux.mosaic.composer.ui

import kotlinx.serialization.Serializable
import uno.lux.mosaic.app.util.AppError
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.NewPostMedia

/** Field limits the composer enforces; they mirror the backend's so errors surface before a publish. */
const val CREATE_POST_TITLE_MAX_LENGTH = 120
const val CREATE_POST_BODY_MAX_LENGTH = 5000

/** Mirrors the server's `Album::MAX_PHOTOS`, so an over-long selection is refused before uploading. */
const val CREATE_POST_MAX_IMAGES = 10

/** Mirrors the server's `Video::MAX_BYTES`, so an oversized clip is refused before it is uploaded. */
const val CREATE_POST_MAX_VIDEO_BYTES = 25L * 1024 * 1024

/**
 * The media attached to a draft. A post carries photos *or* a video, never both, so the states are
 * modelled as a closed hierarchy rather than two independent fields — the illegal combination is
 * unrepresentable here instead of being a rule the form has to remember to enforce.
 *
 * Both variants hold content-URI strings rather than bytes: a selection can run to ten photos or a
 * 25 MB clip, and holding that in memory for the length of the composing session would be wasteful
 * when the thumbnails render straight from the URI. They are read into [FileUpload]s once, at
 * publish (see `CreatePostViewModel.publish`).
 *
 * [Serializable] because a picked selection is part of the draft that survives process death.
 */
@Serializable
sealed interface CreatePostMedia {

    /** Nothing attached — the only state from which either kind can still be chosen. */
    @Serializable
    data object None : CreatePostMedia

    @Serializable
    data class Images(
        val uris: List<String>,
    ) : CreatePostMedia {
        val canAddMore: Boolean
            get() = uris.size < CREATE_POST_MAX_IMAGES
    }

    /**
     * A single clip. [durationSeconds] is read off the file when it is picked, purely to badge the
     * composer's own thumbnail: the clip has not been uploaded yet, so the server's derived
     * duration — the one a published post shows — does not exist for it. It is not sent with the
     * upload; see `NewPostMedia.Video`.
     */
    @Serializable
    data class Video(
        val uri: String,
        val durationSeconds: Int,
    ) : CreatePostMedia
}

/**
 * The composer's editable fields. The text is kept exactly as typed — trimming happens once, in
 * [toNewPost], so a trailing space the user is still typing past doesn't fight the cursor.
 *
 * [toNewPost] takes the already-loaded [media] as a parameter rather than producing it, since
 * reading the picked files needs a suspending `FileLoader` the form has no business holding.
 *
 * [Serializable] so a part-written post survives process death — see [uno.lux.mosaic.util.saveDraft].
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

sealed interface CreatePostError {

    data class Failed(
        val error: AppError,
    ) : CreatePostError

    /** The chosen clip is over [CREATE_POST_MAX_VIDEO_BYTES], which the server would reject anyway. */
    data object VideoTooLarge : CreatePostError
}

data class CreatePostUiState(
    val form: CreatePostForm = CreatePostForm(),
    val isPublishing: Boolean = false,
    val showDiscardConfirmation: Boolean = false,
    val error: CreatePostError? = null,
)
