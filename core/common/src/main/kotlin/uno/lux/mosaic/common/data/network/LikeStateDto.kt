package uno.lux.mosaic.common.data.network

import kotlinx.serialization.Serializable

@Serializable
data class SetLikeRequestDto(
    val liked: Boolean,
)

@Serializable
data class LikeStateDto(
    val isLiked: Boolean,
    val likeCount: Int,
)
