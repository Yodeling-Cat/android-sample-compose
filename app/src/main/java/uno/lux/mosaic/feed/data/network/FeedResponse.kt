package uno.lux.mosaic.feed.data.network

import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.data.network.CursorPageDto
import uno.lux.mosaic.post.data.network.PostDto
import uno.lux.mosaic.user.data.network.SideloadedUsers

/** Top-level feed response — not wrapped in an extra `data` key. */
@Serializable
data class FeedResponse(
    val data: List<PostDto>,
    val included: SideloadedUsers? = null,
    val page: CursorPageDto,
)
