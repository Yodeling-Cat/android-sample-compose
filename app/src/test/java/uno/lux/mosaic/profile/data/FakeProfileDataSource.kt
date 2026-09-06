package uno.lux.mosaic.profile.data

import uno.lux.mosaic.user.data.domain.UserId

internal class FakeProfileDataSource(
    private val refreshData: Map<String, ProfileRefreshData> = emptyMap(),
    private val morePosts: Map<String, PostsPage> = emptyMap(),
    /** Keyed by cursor, so a test can drive a multi-page Saved tab; null is the first page. */
    private val bookmarks: Map<String, Map<String?, PostsPage>> = emptyMap(),
    /** The same, for the Likes tab. */
    private val likes: Map<String, Map<String?, PostsPage>> = emptyMap(),
) : ProfileDataSource {

    /** The (userId, cursor) pairs [bookmarks] was called with, in call order. */
    val bookmarkCalls = mutableListOf<Pair<String, String?>>()

    /** The (userId, cursor) pairs [likes] was called with, in call order. */
    val likeCalls = mutableListOf<Pair<String, String?>>()

    override suspend fun refresh(userId: UserId) =
        refreshData[userId] ?: ProfileRefreshData(postsCount = 0, page = emptyPage())

    override suspend fun loadMorePosts(userId: UserId, cursor: String?) =
        morePosts[userId] ?: emptyPage()

    override suspend fun bookmarks(userId: UserId, cursor: String?): PostsPage {
        bookmarkCalls += userId to cursor

        return bookmarks[userId]?.get(cursor) ?: emptyPage()
    }

    override suspend fun likes(userId: UserId, cursor: String?): PostsPage {
        likeCalls += userId to cursor

        return likes[userId]?.get(cursor) ?: emptyPage()
    }

    private fun emptyPage() = PostsPage(emptyList(), emptyList(), null, false)
}
