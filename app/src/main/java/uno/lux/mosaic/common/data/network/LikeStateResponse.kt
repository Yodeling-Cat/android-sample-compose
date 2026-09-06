package uno.lux.mosaic.common.data.network

import kotlinx.serialization.Serializable

@Serializable
data class LikeStateResponse(
    val data: LikeStateDto,
)
