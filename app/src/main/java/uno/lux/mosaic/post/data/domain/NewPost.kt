package uno.lux.mosaic.post.data.domain

import uno.lux.mosaic.common.data.files.FileUpload

/**
 * The media a draft carries. A post has photos *or* a video, never both.
 */
sealed interface NewPostMedia {

    data object None : NewPostMedia

    data class Images(
        val files: List<FileUpload>,
    ) : NewPostMedia

    /**
     * The file alone: the server derives the clip's duration from it, so nothing about the video
     * beyond its bytes travels with the upload.
     */
    data class Video(
        val file: FileUpload,
    ) : NewPostMedia
}

/**
 * A post the user is about to publish.
 */
data class NewPost(
    val title: String,
    val body: String,
    val media: NewPostMedia = NewPostMedia.None,
)
