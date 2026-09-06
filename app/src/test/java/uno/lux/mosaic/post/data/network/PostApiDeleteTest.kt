package uno.lux.mosaic.post.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uno.lux.mosaic.common.data.network.createApi

/**
 * Pins the `DELETE /posts/:id` **wire contract** by driving the real Retrofit stack over loopback.
 *
 * A faked [PostApi] proves the data source asks to delete the right ID, but not the two things
 * the client and the Rails backend have to agree on: that the request goes out as a `DELETE` to
 * that path, and that the server's empty `204 No Content` is accepted. The latter is why
 * [PostApi.deletePost] returns `Unit` rather than a response envelope — a `@GET`-shaped
 * signature expecting a body would fail only against a real server.
 */
class PostApiDeleteTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: NetworkPostDataSource

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val api = server.createApi(PostApi::class.java)

        dataSource = NetworkPostDataSource(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `delete issues a DELETE to the post's path and accepts an empty 204`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        dataSource.delete("p1")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/posts/p1", request.path)
        assertEquals(0L, request.bodySize)
    }
}
