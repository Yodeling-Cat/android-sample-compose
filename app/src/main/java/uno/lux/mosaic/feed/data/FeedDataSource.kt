package uno.lux.mosaic.feed.data

import uno.lux.mosaic.post.domain.Post
import uno.lux.mosaic.user.domain.User

interface FeedDataSource {
    suspend fun fetch(cursor: String?): FeedPage
}

data class FeedPage(
    val posts: List<Post>,
    val users: List<User>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
