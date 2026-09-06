package uno.lux.mosaic.profile.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import uno.lux.mosaic.post.data.network.PostMapper
import uno.lux.mosaic.profile.data.PostsPage
import uno.lux.mosaic.profile.data.ProfileDataSource
import uno.lux.mosaic.profile.data.ProfileRefreshData
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.user.data.network.toDomain

class NetworkProfileDataSource(
    private val api: ProfileApi,
) : ProfileDataSource {

    override suspend fun refresh(userId: UserId): ProfileRefreshData = withContext(Dispatchers.IO) {
        val statsDeferred = async { api.getProfileStats(userId).data }
        val postsDeferred = async { api.getUserPosts(userId) }

        ProfileRefreshData(
            postsCount = statsDeferred.await().postsCount,
            page = postsDeferred.await().toPage(),
        )
    }

    override suspend fun loadMorePosts(userId: UserId, cursor: String?): PostsPage =
        withContext(Dispatchers.IO) { api.getUserPosts(userId, cursor = cursor).toPage() }

    override suspend fun bookmarks(userId: UserId, cursor: String?): PostsPage =
        withContext(Dispatchers.IO) { api.getBookmarks(userId, cursor = cursor).toPage() }

    override suspend fun likes(userId: UserId, cursor: String?): PostsPage =
        withContext(Dispatchers.IO) { api.getLikes(userId, cursor = cursor).toPage() }

    private fun PostsWithAuthorsResponse.toPage() = PostsPage(
        posts = data.map { PostMapper.map(it) },
        users = included.users.map { it.toDomain() },
        cursor = page.nextCursor,
        hasMore = page.hasMore,
    )
}
