package uno.lux.mosaic.feed.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uno.lux.mosaic.feed.data.FeedDataSource
import uno.lux.mosaic.feed.data.FeedPage
import uno.lux.mosaic.post.data.network.PostMapper
import uno.lux.mosaic.user.data.network.UserMapper

class NetworkFeedDataSource(
    private val api: FeedApi,
) : FeedDataSource {

    override suspend fun fetch(cursor: String?): FeedPage = withContext(Dispatchers.IO) {
        val response = api.getFeed(cursor = cursor, include = "author")
        FeedPage(
            posts = response.data.map { PostMapper.map(it) },
            users = response.included
                ?.users
                .orEmpty()
                .map { UserMapper.map(it) },
            nextCursor = response.page.nextCursor,
            hasMore = response.page.hasMore,
        )
    }
}
