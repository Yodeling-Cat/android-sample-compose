package uno.lux.mosaic.post.data.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.NewPostMedia
import uno.lux.mosaic.testing.createApi

/**
 * Pins the `POST /posts` **wire format** by driving the real Retrofit stack over loopback.
 *
 * The other data-source tests swap in a fake [PostApi], which proves what the data source asks
 * for but not what Retrofit actually serializes — and the multipart details are exactly where the
 * client and the Rails backend have to agree: the `images[]` part name Rack needs to build an
 * array, and the omission (rather than empty encoding) of the optional video parts. Both are
 * invisible to a faked API and would only surface as a failed upload against a real server.
 */
class PostApiMultipartTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: NetworkPostDataSource

    private val createdPostJson =
        """
        {"data":{"id":"p-new","url":"https://mosaic.test/p/p-new","title":"T","body":"B",
        "createdAt":"2025-01-01T00:00:00.000Z","authorId":"u1",
        "likeCount":0,"commentCount":0,"isLiked":false,"isBookmarked":false},
        "included":{"users":[{"id":"u1","nickname":"Ada","handle":"@countess","followerCount":0,
                              "followingCount":0,"isFollowing":false}]}}
        """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setResponseCode(201).setBody(createdPostJson))

        val api = server.createApi(PostApi::class.java)

        dataSource = NetworkPostDataSource(api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun upload(name: String, mimeType: String) =
        FileUpload(bytes = name.toByteArray(), mimeType = mimeType, filename = name)

    @Test
    fun `images are sent as repeated 'images' array parts and no video parts`() = runTest {
        dataSource.create(
            NewPost(
                title = "T",
                body = "B",
                media = NewPostMedia.Images(
                    listOf(upload("one.png", "image/png"), upload("two.png", "image/png")),
                ),
            ),
        )

        val body = server.takeRequest().body.readUtf8()

        // The trailing brackets are what make Rack collect these into one `images` array.
        assertEquals(2, Regex("""name="images\[\]"""").findAll(body).count())
        assertTrue(body.contains("""filename="one.png""""))
        assertTrue(body.contains("""filename="two.png""""))
        // Omitted entirely rather than sent empty — an empty part would reach the server as a
        // present-but-blank video and trip the media-conflict check.
        assertFalse(body.contains("""name="video""""))
    }

    @Test
    fun `a video is sent as a single part, with no image parts`() = runTest {
        dataSource.create(
            NewPost(
                title = "T",
                body = "B",
                media = NewPostMedia.Video(upload("clip.mp4", "video/mp4")),
            ),
        )

        val body = server.takeRequest().body.readUtf8()

        assertEquals(1, Regex("""name="video"""").findAll(body).count())
        assertTrue(body.contains("""filename="clip.mp4""""))
        assertTrue(body.contains("Content-Type: video/mp4"))
        // The file travels alone: the server derives the duration from it, and the composer's own
        // reading of it is for its thumbnail badge only.
        assertFalse(body.contains("videoDurationSeconds"))
        assertFalse(body.contains("images["))
    }

    @Test
    fun `a text-only post sends neither media part`() = runTest {
        dataSource.create(NewPost(title = "T", body = "B"))

        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
        assertTrue(body.contains("""name="title""""))
        assertTrue(body.contains("""name="body""""))
        assertFalse(body.contains("images["))
        assertFalse(body.contains("""name="video""""))
    }
}
