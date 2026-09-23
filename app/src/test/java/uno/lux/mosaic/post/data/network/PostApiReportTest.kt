package uno.lux.mosaic.post.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.testing.createApi

class PostApiReportTest {

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
    fun `report posts the reason and details to the post's report path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        dataSource.report("p1", ReportReason.SPAM, "Posted this five times")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/posts/p1/report", request.path)
        assertEquals(
            """{"reason":"spam","details":"Posted this five times"}""",
            request.body.readUtf8(),
        )
    }

    // The server treats an absent `details` as "no context given"; sending `""` or `null` would
    // both have to be special-cased there instead.
    @Test
    fun `report leaves blank details off the body entirely`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        dataSource.report("p1", ReportReason.OTHER, "")

        assertEquals("""{"reason":"other"}""", server.takeRequest().body.readUtf8())
    }

    /** Written out rather than derived from the enum, so this test is the contract itself. */
    @Test
    fun `every reason goes out in the server's spelling`() = runTest {
        val expected = mapOf(
            ReportReason.SPAM to "spam",
            ReportReason.HARASSMENT to "harassment",
            ReportReason.HATE_SPEECH to "hate_speech",
            ReportReason.MISINFORMATION to "misinformation",
            ReportReason.VIOLENCE to "violence",
            ReportReason.OTHER to "other",
        )
        assertEquals(ReportReason.entries.toSet(), expected.keys)

        expected.forEach { (reason, wire) ->
            server.enqueue(MockResponse().setResponseCode(204))

            dataSource.report("p1", reason, "")

            assertEquals("""{"reason":"$wire"}""", server.takeRequest().body.readUtf8())
        }
    }
}
