package uno.lux.mosaic.composer.ui

sealed interface CreatePostUiEvent {

    data class TitleChanged(
        val value: String,
    ) : CreatePostUiEvent

    data class BodyChanged(
        val value: String,
    ) : CreatePostUiEvent

    data class ImagesPicked(
        val uris: List<String>,
    ) : CreatePostUiEvent

    data class RemoveImage(
        val uri: String,
    ) : CreatePostUiEvent

    data class VideoPicked(
        val uri: String,
    ) : CreatePostUiEvent

    data object RemoveVideo : CreatePostUiEvent

    data class OpenImages(
        val media: CreatePostMedia.Images,
        val initialIndex: Int,
    ) : CreatePostUiEvent

    data class OpenVideo(
        val media: CreatePostMedia.Video,
    ) : CreatePostUiEvent

    data object Publish : CreatePostUiEvent

    data object GoBack : CreatePostUiEvent

    data object DismissDiscard : CreatePostUiEvent

    data object ConfirmDiscard : CreatePostUiEvent
}
