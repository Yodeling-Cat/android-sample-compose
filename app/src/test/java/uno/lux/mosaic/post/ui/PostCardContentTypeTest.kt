package uno.lux.mosaic.post.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.album.data.domain.Album
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.testing.testPostUrl
import uno.lux.mosaic.video.data.domain.Video
import java.time.Instant

class PostCardContentTypeTest {

    private val textPost = Post(
        id = "p1",
        url = testPostUrl("p1"),
        authorId = "u1",
        title = "Title",
        body = "Body",
        createdAt = Instant.EPOCH,
        likeCount = 0,
        commentCount = 0,
    )

    @Test
    fun `a post with no media is a text card`() {
        assertEquals(PostCardContentType.TEXT, textPost.cardContentType)
    }

    @Test
    fun `a post with an album is an album card`() {
        val post = textPost.copy(album = Album(id = "a1", title = "Album", itemCount = 2))

        assertEquals(PostCardContentType.ALBUM, post.cardContentType)
    }

    @Test
    fun `a post with a video is a video card`() {
        val post = textPost.copy(
            video = Video(id = "v1", title = "Video", durationSeconds = 5, videoUrl = "https://example.test/v.mp4"),
        )

        assertEquals(PostCardContentType.VIDEO, post.cardContentType)
    }
}
