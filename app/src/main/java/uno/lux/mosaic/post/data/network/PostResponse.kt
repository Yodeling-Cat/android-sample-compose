package uno.lux.mosaic.post.data.network

import kotlinx.serialization.Serializable
import uno.lux.mosaic.user.data.network.SideloadedUsers

@Serializable
data class PostResponse(
    val data: PostDto,
    val included: SideloadedUsers,
)
