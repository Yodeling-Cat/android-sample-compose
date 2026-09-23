package uno.lux.mosaic.post.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uno.lux.mosaic.testing.createApi

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
