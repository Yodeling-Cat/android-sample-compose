package uno.lux.mosaic.post.data.domain

import uno.lux.mosaic.common.data.files.FileUpload

/**
 * The media a draft carries. A post has photos *or* a video, never both — the server rejects the
 * combination and the clients have no layout for two media blocks — so the alternatives are a
 * closed hierarchy rather than two nullable fields.
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
 * A post the signed-in user is about to publish. The author is derived server-side from the
 * caller, so a draft carries only what the composer collects: the text plus its [media], which the
 * server stores and hands back as the post's [uno.lux.mosaic.album.data.domain.Album] or [uno.lux.mosaic.video.data.domain.Video].
 */
data class NewPost(
    val title: String,
    val body: String,
    val media: NewPostMedia = NewPostMedia.None,
)
