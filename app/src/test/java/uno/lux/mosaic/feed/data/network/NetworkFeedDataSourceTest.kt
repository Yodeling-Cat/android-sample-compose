package uno.lux.mosaic.feed.data.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.common.data.network.CursorPageDto
import uno.lux.mosaic.common.data.network.emptyPage
import uno.lux.mosaic.post.data.network.postDto
import uno.lux.mosaic.user.data.network.SideloadedUsers
import uno.lux.mosaic.user.data.network.userDto

class NetworkFeedDataSourceTest {

    @Test
    fun `fetch maps post DTOs to domain posts`() = runTest {
        val api = FakeFeedApi(
            feedResponse = FeedResponse(
                data = listOf(postDto("p1", "u1"), postDto("p2", "u1")),
                page = emptyPage,
            ),
        )
        val result = NetworkFeedDataSource(api).fetch(cursor = null)

        assertEquals(listOf("p1", "p2"), result.posts.map { it.id })
    }

    @Test
    fun `fetch maps sideloaded authors to domain users`() = runTest {
        val api = FakeFeedApi(
            feedResponse = FeedResponse(
                data = listOf(postDto("p1", "u1")),
                included = SideloadedUsers(users = listOf(userDto("u1", "Ada"), userDto("u2", "Grace"))),
                page = emptyPage,
            ),
        )
        val result = NetworkFeedDataSource(api).fetch(cursor = null)

        assertEquals(listOf("u1", "u2"), result.users.map { it.id })
        assertEquals("Ada", result.users.first().nickname)
    }

    @Test
    fun `fetch maps the isFollowing flag on sideloaded authors`() = runTest {
        val api = FakeFeedApi(
            feedResponse = FeedResponse(
                data = listOf(postDto("p1", "u1")),
                included = SideloadedUsers(users = listOf(userDto("u1", "Ada", isFollowing = true))),
                page = emptyPage,
            ),
        )
        val result = NetworkFeedDataSource(api).fetch(cursor = null)

        assertTrue(result.users.single().isFollowing)
    }

    @Test
    fun `fetch returns empty users when sideload is absent`() = runTest {
        val api = FakeFeedApi(
            feedResponse = FeedResponse(
                data = listOf(postDto("p1", "u1")),
                included = null,
                page = emptyPage,
            ),
        )
        val result = NetworkFeedDataSource(api).fetch(cursor = null)

        assertTrue(result.users.isEmpty())
    }

    @Test
    fun `fetch propagates cursor and hasMore from the page`() = runTest {
        val api = FakeFeedApi(
            feedResponse = FeedResponse(
                data = listOf(postDto("p1", "u1")),
                page = CursorPageDto(nextCursor = "c2", hasMore = true),
            ),
        )
        val result = NetworkFeedDataSource(api).fetch(cursor = null)

        assertEquals("c2", result.nextCursor)
        assertTrue(result.hasMore)
    }

    @Test
    fun `fetch with no more pages returns null cursor and hasMore false`() = runTest {
        val api = FakeFeedApi(feedResponse = FeedResponse(data = emptyList(), page = emptyPage))
        val result = NetworkFeedDataSource(api).fetch(cursor = null)

        assertNull(result.nextCursor)
        assertFalse(result.hasMore)
    }
}
