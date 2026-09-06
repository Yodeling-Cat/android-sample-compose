package uno.lux.mosaic.post.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.data.network.createApi

/**
 * Pins the `POST /posts/:id/report` **wire contract** by driving the real Retrofit stack over
 * loopback.
 *
 * A faked [PostApi] proves the data source reports the right post for the right reason, but not
 * what the Rails backend actually reads: the JSON field names, the *spelling* of each reason
 * (`PostsService::REPORT_REASONS`), the omission of the optional details, and that the empty
 * `204 No Content` a report is answered with is accepted — which is why [PostApi.reportPost]
 * returns `Unit` rather than a response envelope.
 */
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

    /**
     * Every reason, in the spelling the server's `REPORT_REASONS` lists. Written out rather than
     * derived from the enum, so this reads as the contract itself: a renamed constant or a new
     * reason has to be answered here, and the backend's own list updated to match.
     */
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
