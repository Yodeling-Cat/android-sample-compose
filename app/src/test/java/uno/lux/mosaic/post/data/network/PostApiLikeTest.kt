package uno.lux.mosaic.post.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.testing.createApi

class PostApiLikeTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: NetworkPostDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        dataSource = NetworkPostDataSource(server.createApi(PostApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `setLike PUTs the requested state to the post's like path`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{"isLiked":true,"likeCount":42}}"""))

        val settled = dataSource.setLike("p1", liked = true)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/posts/p1/like", request.path)
        assertEquals("""{"liked":true}""", request.body.readUtf8())
        assertEquals(LikeState(isLiked = true, likeCount = 42), settled)
    }

    // Unliking is the same request with the other state, not a different endpoint — which is the
    // whole reason a repeat of either one is harmless.
    @Test
    fun `setLike asks for false the same way it asks for true`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{"isLiked":false,"likeCount":41}}"""))

        dataSource.setLike("p1", liked = false)

        assertEquals("""{"liked":false}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `setBookmark PUTs the requested state to the post's bookmark path`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":{"isBookmarked":true}}"""))

        val settled = dataSource.setBookmark("p1", bookmarked = true)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/posts/p1/bookmark", request.path)
        assertEquals("""{"bookmarked":true}""", request.body.readUtf8())
        assertEquals(true, settled)
    }
}
