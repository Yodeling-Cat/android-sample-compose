package uno.lux.mosaic.profile.data

import uno.lux.mosaic.user.data.domain.UserId
import java.net.UnknownHostException

internal class FakeProfileDataSource(
    private val refreshData: Map<String, ProfileRefreshData> = emptyMap(),
    private val morePosts: Map<String, PostsPage> = emptyMap(),
    private val bookmarks: Map<String, Map<String?, PostsPage>> = emptyMap(),
    private val likes: Map<String, Map<String?, PostsPage>> = emptyMap(),
) : ProfileDataSource {

    val bookmarkCalls = mutableListOf<Pair<String, String?>>()

    val likeCalls = mutableListOf<Pair<String, String?>>()

    var offline = false

    override suspend fun refresh(userId: UserId) =
        refreshData[userId] ?: ProfileRefreshData(postsCount = 0, page = emptyPage())

    override suspend fun loadMorePosts(userId: UserId, cursor: String?): PostsPage {
        failIfOffline()

        return morePosts[userId] ?: emptyPage()
    }

    override suspend fun bookmarks(userId: UserId, cursor: String?): PostsPage {
        bookmarkCalls += userId to cursor
        failIfOffline()

        return bookmarks[userId]?.get(cursor) ?: emptyPage()
    }

    override suspend fun likes(userId: UserId, cursor: String?): PostsPage {
        likeCalls += userId to cursor
        failIfOffline()

        return likes[userId]?.get(cursor) ?: emptyPage()
    }

    private fun failIfOffline() {
        if (offline) throw UnknownHostException("offline")
    }

    private fun emptyPage() = PostsPage(emptyList(), emptyList(), null, false)
}
