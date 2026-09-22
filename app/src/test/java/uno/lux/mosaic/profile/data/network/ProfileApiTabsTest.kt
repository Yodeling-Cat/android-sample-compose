package uno.lux.mosaic.profile.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uno.lux.mosaic.testing.createApi

/**
 * Pins the **wire format** of the profile's three lists — `GET /users/:id/posts`,
 * `GET /users/:id/bookmarks` and `GET /users/:id/likes`, which share one response shape — by
 * driving the real Retrofit stack over loopback against a body captured verbatim from the Rails
 * backend.
 *
 * [NetworkProfileDataSourceTest] fakes [ProfileApi], which proves what the data source does with a
 * response but not that the response parses. The parts client and server have to agree on live
 * here: the request paths, the snake_case `page` keys next to the camelCase item fields, and the
 * `included.users` sideload no tab can render without.
 */
class ProfileApiTabsTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: NetworkProfileDataSource

    // Captured from the running backend: GET /api/users/u1/bookmarks as u1.
    private val bookmarksJson =
        """
        {"data":[
          {"id":"p2","url":"https://mosaic.test/p/p2",
           "title":"Found the bug","body":"It was an actual moth.",
           "createdAt":"2026-07-20T11:34:00Z","likeCount":342,"commentCount":51,
           "isLiked":false,"isBookmarked":true,"album":null,"video":null,"authorId":"u2"},
          {"id":"p4","url":"https://mosaic.test/p/p4",
           "title":"Priority scheduling saved the landing","body":"A 1202 alarm.",
           "createdAt":"2026-07-20T06:12:00Z","likeCount":1543,"commentCount":88,
           "isLiked":true,"isBookmarked":true,
           "album":{"id":"pa4","title":"Launch room","itemCount":1,
                    "images":["https://cdn.test/1.jpg"]},
           "video":null,"authorId":"u4"}],
         "page":{"next_cursor":"djE6MTc4NDU0NzI0MDMxNDpwMg","has_more":true},
         "included":{"users":[
           {"id":"u2","nickname":"Grace Hopper","handle":"@amazinggrace","age":85,
            "gender":"Female","location":"Arlington, VA",
            "bio":"Rear Admiral. Compiler pioneer.","avatarUrl":null,
            "followerCount":9821,"followingCount":42,"isFollowing":false},
           {"id":"u4","nickname":"Margaret Hamilton","handle":"@mhamilton","age":88,
            "gender":"Female","location":"Cambridge, MA",
            "bio":"I coined \"software engineering\".","avatarUrl":null,
            "followerCount":7310,"followingCount":15,"isFollowing":true}]}}
        """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(200).setBody(bookmarksJson))

        val api = server.createApi(ProfileApi::class.java)

        dataSource = NetworkProfileDataSource(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `bookmarks are requested from the user's own bookmarks path`() = runTest {
        dataSource.bookmarks("u1", cursor = "c2")

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/users/u1/bookmarks?cursor=c2&limit=20", request.path)
    }

    @Test
    fun `a real backend response parses into saved posts`() = runTest {
        val page = dataSource.bookmarks("u1", cursor = null)

        assertEquals(listOf("p2", "p4"), page.posts.map { it.id })
        assertTrue(page.posts.all { it.isBookmarked })
        assertEquals(listOf("https://cdn.test/1.jpg"), page.posts[1].album?.images)
    }

    @Test
    fun `the sideloaded authors parse into domain users`() = runTest {
        val page = dataSource.bookmarks("u1", cursor = null)

        assertEquals(listOf("u2", "u4"), page.users.map { it.id })
        assertEquals("Grace Hopper", page.users.first().nickname)
        assertFalse(page.users.first().isFollowing)
        assertTrue(page.users[1].isFollowing)
    }

    /**
     * The sideload is the *same* projection `GET /users/:id` serves, not an identity-only subset.
     * That is what lets these users go straight into the cache: a partial one would be
     * indistinguishable from a user who has genuinely left the fields empty, so ingesting it
     * would blank the bio and identity chips of a profile already loaded in full.
     */
    @Test
    fun `the sideloaded authors carry full profile detail`() = runTest {
        val grace = dataSource.bookmarks("u1", cursor = null).users.first()

        assertEquals(85, grace.age)
        assertEquals("Female", grace.gender)
        assertEquals("Arlington, VA", grace.location)
        assertEquals("Rear Admiral. Compiler pioneer.", grace.bio)
        assertEquals(9821, grace.followerCount)
        assertEquals(42, grace.followingCount)
    }

    @Test
    fun `the snake_case page meta parses into the cursor and hasMore`() = runTest {
        val page = dataSource.bookmarks("u1", cursor = null)

        assertEquals("djE6MTc4NDU0NzI0MDMxNDpwMg", page.cursor)
        assertTrue(page.hasMore)
    }

    // Likes share the shape above, so what is left to pin is that they are asked for at their
    // own path — a copy-paste that reused /bookmarks would parse perfectly and be wrong.
    @Test
    fun `likes are requested from the likes path and parse the same shape`() = runTest {
        val page = dataSource.likes("u1", cursor = null)

        assertEquals("/api/users/u1/likes?limit=20", server.takeRequest().path)
        assertEquals(listOf("p2", "p4"), page.posts.map { it.id })
        assertEquals(listOf("u2", "u4"), page.users.map { it.id })
    }

    // The Posts tab reads the same shape, sideload included — that is the whole point of it
    // answering like the other two. Only its path is its own.
    @Test
    fun `paging the profile's own posts uses the posts path and parses the sideload`() = runTest {
        val page = dataSource.loadMorePosts("u1", cursor = "c2")

        assertEquals("/api/users/u1/posts?cursor=c2&limit=20", server.takeRequest().path)
        assertEquals(listOf("p2", "p4"), page.posts.map { it.id })
        assertEquals(listOf("u2", "u4"), page.users.map { it.id })
    }
}
