package uno.lux.mosaic.profile.data.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.common.data.network.CursorPageDto
import uno.lux.mosaic.common.data.network.emptyPage
import uno.lux.mosaic.post.data.network.PostDto
import uno.lux.mosaic.post.data.network.postDto
import uno.lux.mosaic.user.data.network.SideloadedUsers
import uno.lux.mosaic.user.data.network.UserDto
import uno.lux.mosaic.user.data.network.userDto

class NetworkProfileDataSourceTest {

    private fun postsResponse(
        data: List<PostDto>,
        users: List<UserDto> = emptyList(),
        page: CursorPageDto = emptyPage,
    ) = PostsWithAuthorsResponse(data = data, included = SideloadedUsers(users = users), page = page)

    private fun bookmarksResponse(
        data: List<PostDto>,
        users: List<UserDto> = emptyList(),
        page: CursorPageDto = emptyPage,
    ) = postsResponse(data, users, page)

    @Test
    fun `refresh maps the profile's posts and its stats`() = runTest {
        val api = FakeProfileApi(
            profileStats = mapOf("u1" to ProfileStatsDto(postsCount = 2)),
            userPostsResponse = postsResponse(
                data = listOf(postDto("p1", "u1"), postDto("p6", "u1")),
                page = CursorPageDto(nextCursor = "c2", hasMore = true),
            ),
        )
        val result = NetworkProfileDataSource(api).refresh("u1")

        assertEquals(2, result.postsCount)
        assertEquals(listOf("p1", "p6"), result.page.posts.map { it.id })
        assertEquals("c2", result.page.cursor)
        assertTrue(result.page.hasMore)
    }

    // The Posts tab sideloads its author too, so a profile opened cold — from a deep link or
    // after process death — has the user it needs to render a row without a second request.
    @Test
    fun `refresh maps the sideloaded author to a domain user`() = runTest {
        val api = FakeProfileApi(
            userPostsResponse = postsResponse(
                data = listOf(postDto("p1", "u1")),
                users = listOf(userDto("u1", "Ada")),
            ),
        )
        val result = NetworkProfileDataSource(api).refresh("u1")

        assertEquals(listOf("Ada"), result.page.users.map { it.nickname })
    }

    @Test
    fun `refresh requests the first page, with no cursor`() = runTest {
        val api = FakeProfileApi()

        NetworkProfileDataSource(api).refresh("u1")

        assertEquals(listOf("u1" to null), api.userPostCalls)
    }

    @Test
    fun `loadMorePosts requests the given cursor and maps posts with their author`() = runTest {
        val api = FakeProfileApi(
            userPostsResponse = postsResponse(
                data = listOf(postDto("p6", "u1")),
                users = listOf(userDto("u1", "Ada")),
            ),
        )
        val result = NetworkProfileDataSource(api).loadMorePosts("u1", cursor = "c2")

        assertEquals(listOf("u1" to "c2"), api.userPostCalls)
        assertEquals(listOf("p6"), result.posts.map { it.id })
        assertEquals(listOf("u1"), result.users.map { it.id })
    }

    @Test
    fun `bookmarks maps post DTOs to domain posts`() = runTest {
        val api = FakeProfileApi(
            bookmarksResponse = bookmarksResponse(
                data = listOf(postDto("p2", "u2"), postDto("p4", "u4")),
            ),
        )
        val result = NetworkProfileDataSource(api).bookmarks("u1", cursor = null)

        assertEquals(listOf("p2", "p4"), result.posts.map { it.id })
    }

    // A saved post can be by anyone, so its author always arrives with the page — the caller
    // has no other way to render the card.
    @Test
    fun `bookmarks maps the sideloaded authors to domain users`() = runTest {
        val api = FakeProfileApi(
            bookmarksResponse = bookmarksResponse(
                data = listOf(postDto("p2", "u2")),
                users = listOf(userDto("u2", "Grace", isFollowing = true)),
            ),
        )
        val result = NetworkProfileDataSource(api).bookmarks("u1", cursor = null)

        val author = result.users.single()
        assertEquals("u2", author.id)
        assertEquals("Grace", author.nickname)
        assertTrue(author.isFollowing)
    }

    @Test
    fun `bookmarks requests the given user and cursor`() = runTest {
        val api = FakeProfileApi()

        NetworkProfileDataSource(api).bookmarks("u1", cursor = "c2")

        assertEquals(listOf("u1" to "c2"), api.bookmarkCalls)
    }

    @Test
    fun `bookmarks propagates cursor and hasMore from the page`() = runTest {
        val api = FakeProfileApi(
            bookmarksResponse = bookmarksResponse(
                data = listOf(postDto("p2", "u2")),
                page = CursorPageDto(nextCursor = "c2", hasMore = true),
            ),
        )
        val result = NetworkProfileDataSource(api).bookmarks("u1", cursor = null)

        assertEquals("c2", result.cursor)
        assertTrue(result.hasMore)
    }

    @Test
    fun `bookmarks with an empty page yields no posts and no cursor`() = runTest {
        val result = NetworkProfileDataSource(FakeProfileApi()).bookmarks("u1", cursor = null)

        assertTrue(result.posts.isEmpty())
        assertNull(result.cursor)
    }

    @Test
    fun `likes maps post DTOs and their sideloaded authors`() = runTest {
        val api = FakeProfileApi(
            likesResponse = postsResponse(
                data = listOf(postDto("p3", "u3"), postDto("p4", "u4")),
                users = listOf(userDto("u3", "Alan"), userDto("u4", "Margaret")),
            ),
        )
        val result = NetworkProfileDataSource(api).likes("u1", cursor = null)

        assertEquals(listOf("p3", "p4"), result.posts.map { it.id })
        assertEquals(listOf("Alan", "Margaret"), result.users.map { it.nickname })
    }

    // Likes and bookmarks are separate lists behind separate endpoints — asking for one
    // must not touch the other.
    @Test
    fun `likes requests the likes endpoint, not bookmarks`() = runTest {
        val api = FakeProfileApi()

        NetworkProfileDataSource(api).likes("u2", cursor = "c3")

        assertEquals(listOf("u2" to "c3"), api.likeCalls)
        assertTrue(api.bookmarkCalls.isEmpty())
    }

    @Test
    fun `likes propagates cursor and hasMore from the page`() = runTest {
        val api = FakeProfileApi(
            likesResponse = postsResponse(
                data = listOf(postDto("p3", "u3")),
                page = CursorPageDto(nextCursor = "c2", hasMore = true),
            ),
        )
        val result = NetworkProfileDataSource(api).likes("u1", cursor = null)

        assertEquals("c2", result.cursor)
        assertTrue(result.hasMore)
    }
}
