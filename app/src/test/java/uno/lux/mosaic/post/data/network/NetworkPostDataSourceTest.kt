package uno.lux.mosaic.post.data.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.common.data.network.LikeStateDto
import uno.lux.mosaic.common.data.network.SetLikeRequestDto
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.NewPostMedia
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.network.userDto
import java.net.UnknownHostException

class NetworkPostDataSourceTest {

    @Test
    fun `fetch maps the post and the author sideloaded beside it`() = runTest {
        val api = FakePostApi(
            postById = mapOf("p1" to postDto("p1", authorId = "u7")),
            usersById = mapOf("u7" to userDto("u7", "Grace")),
        )
        val dataSource = NetworkPostDataSource(api)

        val fetched = dataSource.fetch("p1")!!

        assertEquals(listOf("p1"), api.fetchedPostIds)
        assertEquals("p1", fetched.post.id)
        assertEquals("u7", fetched.post.authorId)
        assertEquals(listOf("Grace"), fetched.users.map { it.nickname })
    }

    // The sideload is carried across verbatim rather than searched for the post's own author, so
    // a payload that named an author it did not send arrives short a user instead of failing.
    @Test
    fun `fetch carries an empty sideload across rather than failing on it`() = runTest {
        val api = FakePostApi(
            postById = mapOf("p1" to postDto("p1", authorId = "u7")),
            usersById = emptyMap(),
        )
        val dataSource = NetworkPostDataSource(api)

        val fetched = dataSource.fetch("p1")!!

        assertEquals("p1", fetched.post.id)
        assertEquals(emptyList<User>(), fetched.users)
    }

    // A 404 is the server saying the post is gone. It has to arrive as an answer the caller can
    // render, not as the exception every other failure raises — the two mean different screens.
    @Test
    fun `fetch returns null for a post the server does not have`() = runTest {
        val dataSource = NetworkPostDataSource(FakePostApi())

        assertNull(dataSource.fetch("gone"))
    }

    @Test
    fun `fetch lets any other failure through`() = runTest {
        val api = FakePostApi().apply { getPostError = UnknownHostException("offline") }
        val dataSource = NetworkPostDataSource(api)

        assertThrows(UnknownHostException::class.java) {
            runBlocking { dataSource.fetch("p1") }
        }
    }

    @Test
    fun `create sends the draft and maps the created post`() = runTest {
        val api = FakePostApi()
        val dataSource = NetworkPostDataSource(api)

        val created = dataSource.create(NewPost(title = "Title", body = "Body"))

        val parts = api.lastCreatePostParts
        assertEquals("Title", parts?.title)
        assertEquals("Body", parts?.body)
        assertEquals(emptyList<FakePostApi.UploadedPart>(), parts?.images)
        assertEquals("p-new", created.post.id)
        assertEquals("Title", created.post.title)
        assertEquals("Body", created.post.body)
        assertEquals("u1", created.post.authorId)
        assertEquals(0, created.post.likeCount)
    }

    @Test
    fun `create uploads each image as its own part, in order`() = runTest {
        val api = FakePostApi()
        val dataSource = NetworkPostDataSource(api)
        val images = listOf(
            FileUpload(bytes = byteArrayOf(1, 2), mimeType = "image/png", filename = "one.png"),
            FileUpload(bytes = byteArrayOf(3, 4), mimeType = "image/jpeg", filename = "two.jpg"),
        )

        dataSource.create(
            NewPost(title = "Title", body = "Body", media = NewPostMedia.Images(images)),
        )

        val parts = api.lastCreatePostParts
        assertEquals(
            listOf(
                FakePostApi.UploadedPart(filename = "one.png", bytes = byteArrayOf(1, 2)),
                FakePostApi.UploadedPart(filename = "two.jpg", bytes = byteArrayOf(3, 4)),
            ),
            parts?.images,
        )
        // Media is exclusive on the wire, not just in the model.
        assertNull(parts?.video)
    }

    @Test
    fun `create uploads a video as a single part`() = runTest {
        val api = FakePostApi()
        val dataSource = NetworkPostDataSource(api)
        val clip = FileUpload(bytes = byteArrayOf(9), mimeType = "video/mp4", filename = "clip.mp4")

        dataSource.create(
            NewPost(
                title = "Title",
                body = "Body",
                media = NewPostMedia.Video(clip),
            ),
        )

        val parts = api.lastCreatePostParts
        assertEquals(
            FakePostApi.UploadedPart(filename = "clip.mp4", bytes = byteArrayOf(9)),
            parts?.video,
        )
        assertEquals(emptyList<FakePostApi.UploadedPart>(), parts?.images)
    }

    @Test
    fun `create returns the sideloaded author as a domain user`() = runTest {
        val dataSource = NetworkPostDataSource(FakePostApi())

        val created = dataSource.create(NewPost(title = "Title", body = "Body"))

        assertEquals(listOf("u1"), created.users.map { it.id })
        assertEquals(listOf("Ada"), created.users.map { it.nickname })
    }

    @Test
    fun `delete asks the API to remove that post`() = runTest {
        val api = FakePostApi()
        val dataSource = NetworkPostDataSource(api)

        dataSource.delete("p1")

        assertEquals(listOf("p1"), api.deletedPostIds)
    }

    @Test
    fun `setLike answers with the isLiked and likeCount the server settled on`() = runTest {
        val api = FakePostApi(likeResult = LikeStateDto(isLiked = true, likeCount = 6))
        val dataSource = NetworkPostDataSource(api)

        val result = dataSource.setLike("p1", liked = true)

        assertEquals(LikeState(isLiked = true, likeCount = 6), result)
    }

    // The state is named in the body rather than left to the server to flip, which is what makes
    // a retry after a timeout land where the first attempt would have.
    @Test
    fun `setLike sends the state the caller asked for`() = runTest {
        val api = FakePostApi()
        val dataSource = NetworkPostDataSource(api)

        dataSource.setLike("p1", liked = false)

        assertEquals(listOf("p1" to SetLikeRequestDto(liked = false)), api.likeRequests)
    }

    @Test
    fun `setBookmark sends the state the caller asked for and answers the server's`() = runTest {
        val api = FakePostApi(bookmarkResult = BookmarkStateDto(isBookmarked = true))
        val dataSource = NetworkPostDataSource(api)

        val result = dataSource.setBookmark("p1", bookmarked = true)

        assertEquals(listOf("p1" to SetBookmarkRequestDto(bookmarked = true)), api.bookmarkRequests)
        assertTrue(result)
    }

    @Test
    fun `report sends the reason in the server's own spelling, with the details`() = runTest {
        val api = FakePostApi()
        val dataSource = NetworkPostDataSource(api)

        dataSource.report("p1", ReportReason.HATE_SPEECH, "Read the third paragraph")

        assertEquals(
            listOf(
                "p1" to ReportPostRequestDto(
                    reason = ReportReasonDto.HATE_SPEECH,
                    details = "Read the third paragraph",
                ),
            ),
            api.reports,
        )
    }

    // The free-text field is optional, so typing nothing has to reach the server as an absent
    // field rather than an empty one it would have to treat as absent anyway.
    @Test
    fun `report omits details the reporter left blank`() = runTest {
        val api = FakePostApi()
        val dataSource = NetworkPostDataSource(api)

        dataSource.report("p1", ReportReason.SPAM, "   ")

        assertNull(
            api.reports
                .single()
                .second.details,
        )
    }
}
