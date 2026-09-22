package uno.lux.mosaic.profile.data

import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

interface ProfileDataSource {
    suspend fun refresh(userId: UserId): ProfileRefreshData

    suspend fun loadMorePosts(userId: UserId, cursor: String?): PostsPage

    suspend fun bookmarks(userId: UserId, cursor: String?): PostsPage

    suspend fun likes(userId: UserId, cursor: String?): PostsPage
}

/** The profile's counts plus the first [page] of its own posts, which arrive in one refresh. */
data class ProfileRefreshData(
    val postsCount: Int,
    val page: PostsPage,
)

/**
 * One page of a list on the profile.
 */
data class PostsPage(
    val posts: List<Post>,
    val users: List<User>,
    val cursor: String?,
    val hasMore: Boolean,
)
