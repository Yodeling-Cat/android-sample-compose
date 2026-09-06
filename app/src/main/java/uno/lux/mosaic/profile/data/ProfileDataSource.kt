package uno.lux.mosaic.profile.data

import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

interface ProfileDataSource {
    suspend fun refresh(userId: UserId): ProfileRefreshData

    suspend fun loadMorePosts(userId: UserId, cursor: String?): PostsPage

    /**
     * A page of the posts [userId] saved. Fetched separately from [refresh] because the Saved
     * tab is private to its owner and loads only once they open it — a profile view never
     * requests it, and the server refuses it for anyone but the signed-in user.
     */
    suspend fun bookmarks(userId: UserId, cursor: String?): PostsPage

    /**
     * A page of the posts [userId] liked. Public, unlike [bookmarks] — anyone may read anyone's
     * — but loaded on the same on-demand terms, since it is a request the Posts tab doesn't need.
     */
    suspend fun likes(userId: UserId, cursor: String?): PostsPage
}

/** The profile's counts plus the first [page] of its own posts, which arrive in one refresh. */
data class ProfileRefreshData(
    val postsCount: Int,
    val page: PostsPage,
)

/**
 * One page of any of the profile's three lists. [users] carries the page's authors: on the Saved
 * and Likes tabs a post is by an arbitrary author the caller may not know yet, and on the Posts
 * tab it is the profile's own user — which a page opened cold has no other way to resolve.
 */
data class PostsPage(
    val posts: List<Post>,
    val users: List<User>,
    val cursor: String?,
    val hasMore: Boolean,
)
