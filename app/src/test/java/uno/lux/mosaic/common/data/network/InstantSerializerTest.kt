package uno.lux.mosaic.common.data.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.post.data.network.PostDto
import java.time.Instant

class InstantSerializerTest {

    @Test
    fun `decodes an ISO-8601 timestamp field into an Instant`() {
        val json =
            """
            {"id":"p1","url":"https://mosaic.test/p/p1","title":"T","body":"B",
            "createdAt":"2025-01-01T00:00:00.000Z","authorId":"u1","likeCount":0,
            "commentCount":0,"isLiked":false,"isBookmarked":false}
            """.trimIndent()

        val dto = Json.decodeFromString<PostDto>(json)

        assertEquals(Instant.parse("2025-01-01T00:00:00.000Z"), dto.createdAt)
    }

    @Test
    fun `round-trips an Instant through its ISO-8601 string`() {
        val instant = Instant.parse("2025-06-01T12:30:00Z")

        val encoded = Json.encodeToString(InstantSerializer, instant)

        assertEquals("\"2025-06-01T12:30:00Z\"", encoded)
        assertEquals(instant, Json.decodeFromString(InstantSerializer, encoded))
    }
}
