package uno.lux.mosaic.post.data.domain

import uno.lux.mosaic.common.data.files.FileUpload

sealed interface NewPostMedia {

    data object None : NewPostMedia

    data class Images(
        val files: List<FileUpload>,
    ) : NewPostMedia

    data class Video(
        val file: FileUpload,
    ) : NewPostMedia
}

data class NewPost(
    val title: String,
    val body: String,
    val media: NewPostMedia = NewPostMedia.None,
)
