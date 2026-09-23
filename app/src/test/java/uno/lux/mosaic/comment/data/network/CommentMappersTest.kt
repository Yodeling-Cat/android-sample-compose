package uno.lux.mosaic.comment.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.user.data.network.UserDto
import java.time.Instant

class CommentMappersTest {

    @Test
    fun `CommentMapper maps the nested author`() {
        val dto = CommentDto(
            id = "c1",
            text = "Hello",
            createdAt = Instant.parse("2025-06-01T12:00:00Z"),
            likeCount = 2,
            isLiked = true,
            author = UserDto(
                id = "u1",
                nickname = "Ada",
                handle = "@ada",
                followerCount = 5,
                followingCount = 1,
                isFollowing = true,
            ),
        )

        val comment = CommentMapper.map(dto)

        assertEquals("u1", comment.author.id)
        assertEquals("Ada", comment.author.nickname)
        assertEquals(true, comment.author.isFollowing)
        assertEquals(dto.createdAt, comment.createdAt)
    }
}
