package uno.lux.mosaic.profile.data.network

import kotlinx.serialization.Serializable
import uno.lux.mosaic.common.data.network.CursorPageDto
import uno.lux.mosaic.post.data.network.PostDto
import uno.lux.mosaic.user.data.network.SideloadedUsers

/**
 * `{ "data": [...], "included": { "users": [...] }, "page": { ... } }` — every one of the
 * profile's three lists: its own posts, its saved posts and its liked ones. [included] is not
 * optional the way it is on the feed, so a page renders from the page alone. On the Posts tab
 * that sideload is only ever the profile's own user; on the other two the authors are arbitrary,
 * and it is the caller's only way to know them.
 */
@Serializable
data class PostsWithAuthorsResponse(
    val data: List<PostDto>,
    val included: SideloadedUsers,
    val page: CursorPageDto,
)
