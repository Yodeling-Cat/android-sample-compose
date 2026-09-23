package uno.lux.mosaic.user.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.user.data.domain.User

class UserMapperTest {

    @Test
    fun `maps every field, optional ones included`() {
        val dto = UserDto(
            id = "u1",
            nickname = "Ada",
            handle = "@ada",
            age = 36,
            gender = "female",
            location = "London",
            bio = "Analyst",
            avatarUrl = "https://example.com/ada.png",
            followerCount = 5,
            followingCount = 1,
            isFollowing = true,
        )

        val expected = User(
            id = "u1",
            nickname = "Ada",
            handle = "@ada",
            age = 36,
            gender = "female",
            location = "London",
            bio = "Analyst",
            avatarUrl = "https://example.com/ada.png",
            followerCount = 5,
            followingCount = 1,
            isFollowing = true,
        )

        assertEquals(expected, UserMapper.map(dto))
    }

    @Test
    fun `keeps a field the user left empty as null`() {
        val dto = UserDto(
            id = "u1",
            nickname = "Ada",
            handle = "@ada",
            followerCount = 0,
            followingCount = 0,
            isFollowing = false,
        )

        val user = UserMapper.map(dto)

        assertEquals(null, user.bio)
        assertEquals(null, user.age)
    }
}
