package uno.lux.mosaic.post.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uno.lux.mosaic.testing.testPostUrl
import java.time.Instant

/**
 * Covers what Mappie can't verify structurally on its own — the nested album/video mapping.
 * (The createdAt timestamp arrives already parsed from the DTO layer; its parsing is covered by
 * [uno.lux.mosaic.core.network.InstantSerializerTest].)
 */
class PostMappersTest {

    @Test
    fun `PostMapper carries scalar fields across unchanged`() {
        val createdAt = Instant.parse("2025-06-01T12:00:00Z")

        val post = PostMapper.map(dto(createdAt = createdAt))

        assertEquals("p1", post.id)
        assertEquals(testPostUrl("p1"), post.url)
        assertEquals("u1", post.authorId)
        assertEquals("Title p1", post.title)
        assertEquals("Body p1", post.body)
        assertEquals(createdAt, post.createdAt)
        assertEquals(6, post.likeCount)
        assertEquals(2, post.commentCount)
        assertEquals(true, post.isLiked)
        assertEquals(true, post.isBookmarked)
    }

    @Test
    fun `PostMapper maps a nested album via AlbumMapper`() {
        val mapped = dto(
            album = AlbumDto(id = "a1", title = "Trip", itemCount = 9, images = listOf("i1", "i2")),
        )

        val album = PostMapper.map(mapped).album!!

        assertEquals("a1", album.id)
        assertEquals("Trip", album.title)
        assertEquals(9, album.itemCount)
        assertEquals(listOf("i1", "i2"), album.images)
    }

    @Test
    fun `PostMapper maps a nested video via VideoMapper`() {
        val mapped = dto(
            video = VideoDto(id = "v1", title = "Clip", durationSeconds = 42, videoUrl = "http://v"),
        )

        val video = PostMapper.map(mapped).video!!

        assertEquals("v1", video.id)
        assertEquals(42, video.durationSeconds)
        assertEquals("http://v", video.videoUrl)
    }

    @Test
    fun `PostMapper carries a video's resolution and thumbnail across`() {
        val mapped = dto(
            video = VideoDto(
                id = "v1",
                title = "Clip",
                durationSeconds = 42,
                videoUrl = "http://v",
                width = 1920,
                height = 1080,
                thumbnailUrl = "http://t.jpg",
                thumbnailWidth = 720,
                thumbnailHeight = 405,
            ),
        )

        val video = PostMapper.map(mapped).video!!

        assertEquals(1920, video.width)
        assertEquals(1080, video.height)
        assertEquals("http://t.jpg", video.thumbnailUrl)
        assertEquals(720, video.thumbnailWidth)
        assertEquals(405, video.thumbnailHeight)
    }

    /**
     * A clip the server could not probe publishes without a resolution or a poster frame, so the
     * fields are absent from the payload entirely rather than sent as zeroes.
     */
    @Test
    fun `PostMapper leaves a video's resolution and thumbnail null when the server sent none`() {
        val mapped = dto(
            video = VideoDto(id = "v1", title = "Clip", durationSeconds = 0, videoUrl = "http://v"),
        )

        val video = PostMapper.map(mapped).video!!

        assertNull(video.width)
        assertNull(video.height)
        assertNull(video.thumbnailUrl)
        assertNull(video.thumbnailWidth)
        assertNull(video.thumbnailHeight)
    }

    @Test
    fun `PostMapper leaves album and video null when absent`() {
        val post = PostMapper.map(dto())

        assertNull(post.album)
        assertNull(post.video)
    }

    /**
     * The shared fixture with every scalar set to something distinctive, so a mapping that drops
     * a field or crosses two of them fails rather than matching a default by luck.
     */
    private fun dto(
        createdAt: Instant = Instant.parse("2025-01-01T00:00:00Z"),
        album: AlbumDto? = null,
        video: VideoDto? = null,
    ) = postDto(
        id = "p1",
        authorId = "u1",
        createdAt = createdAt,
        isLiked = true,
        isBookmarked = true,
        likeCount = 6,
        commentCount = 2,
        album = album,
        video = video,
    )
}
