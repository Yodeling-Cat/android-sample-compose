package uno.lux.mosaic.post.data.network

import kotlinx.serialization.Serializable

@Serializable
data class SetBookmarkRequestDto(
    val bookmarked: Boolean,
)

@Serializable
data class BookmarkStateDto(
    val isBookmarked: Boolean,
)
