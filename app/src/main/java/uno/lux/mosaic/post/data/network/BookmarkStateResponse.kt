package uno.lux.mosaic.post.data.network

import kotlinx.serialization.Serializable

@Serializable
data class BookmarkStateResponse(
    val data: BookmarkStateDto,
)
