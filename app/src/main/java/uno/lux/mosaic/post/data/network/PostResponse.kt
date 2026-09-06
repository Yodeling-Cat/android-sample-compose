package uno.lux.mosaic.post.data.network

import kotlinx.serialization.Serializable
import uno.lux.mosaic.user.data.network.SideloadedUsers

/**
 * `{ "data": { ... }, "included": { "users": [...] } }` — the answer to both endpoints that
 * serve a single post: fetching one and publishing one. [included] holds the post's author, the
 * one user a caller holding nothing but its `authorId` has no other way to resolve, and it is
 * not optional the way the feed's is: a page renders from the response alone.
 */
@Serializable
data class PostResponse(
    val data: PostDto,
    val included: SideloadedUsers,
)
