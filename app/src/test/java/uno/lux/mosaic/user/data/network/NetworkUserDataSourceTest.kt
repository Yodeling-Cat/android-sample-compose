package uno.lux.mosaic.user.data.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import java.net.UnknownHostException

class NetworkUserDataSourceTest {

    // A 404 is the server saying the user is gone. It has to arrive as an answer the caller can
    // render — a restored Screen.Profile key can name a user the server has since deleted, and
    // that page must read "not found", not spin on a retryable error.
    @Test
    fun `fetch returns null for a user the server does not have`() = runTest {
        val dataSource = NetworkUserDataSource(FakeUserApi())

        assertNull(dataSource.fetch("gone"))
    }

    @Test
    fun `fetch lets any other failure through`() = runTest {
        val api = FakeUserApi().apply { getUserError = UnknownHostException("offline") }
        val dataSource = NetworkUserDataSource(api)

        assertThrows(UnknownHostException::class.java) {
            runBlocking { dataSource.fetch("u1") }
        }
    }

    @Test
    fun `fetch returns the user mapped from the API response`() = runTest {
        val api = FakeUserApi(userById = mapOf("u1" to userDto("u1", "Ada")))
        val dataSource = NetworkUserDataSource(api)

        val user = dataSource.fetch("u1")!!

        assertEquals("u1", user.id)
        assertEquals("Ada", user.nickname)
    }

    @Test
    fun `fetch maps all user fields`() = runTest {
        val dto = userDto("u1", "Ada").copy(
            handle = "@ada",
            age = 30,
            gender = "Woman",
            location = "London",
            bio = "Mathematician",
            avatarUrl = "https://example.com/ada.jpg",
            followerCount = 500,
            followingCount = 10,
        )
        val api = FakeUserApi(userById = mapOf("u1" to dto))
        val dataSource = NetworkUserDataSource(api)

        val user = dataSource.fetch("u1")!!

        assertEquals("@ada", user.handle)
        assertEquals(30, user.age)
        assertEquals("Woman", user.gender)
        assertEquals("London", user.location)
        assertEquals("Mathematician", user.bio)
        assertEquals("https://example.com/ada.jpg", user.avatarUrl)
        assertEquals(500, user.followerCount)
        assertEquals(10, user.followingCount)
    }

    @Test
    fun `fetch maps the isFollowing flag`() = runTest {
        val api = FakeUserApi(userById = mapOf("u1" to userDto("u1", "Ada", isFollowing = true)))
        val dataSource = NetworkUserDataSource(api)

        assertTrue(dataSource.fetch("u1")!!.isFollowing)
    }

    @Test
    fun `update sends text fields as multipart parts and returns the updated user`() = runTest {
        val api = FakeUserApi(userById = mapOf("u1" to userDto("u1", "Ada")))
        val dataSource = NetworkUserDataSource(api)

        val user = dataSource.update(
            "u1",
            ProfileUpdate(
                nickname = "Ada King",
                age = 37,
                gender = "Woman",
                bio = null,
                avatar = null,
            ),
        )

        assertEquals("Ada King", user.nickname)
        assertEquals(37, user.age)
        assertEquals("Woman", user.gender)
        assertNull(user.bio) // sent as an empty part, cleared server-side
        assertNull("no avatar part when none was chosen", api.lastAvatarPart)
    }

    @Test
    fun `update uploads the avatar as a file part`() = runTest {
        val api = FakeUserApi(userById = mapOf("u1" to userDto("u1", "Ada")))
        val dataSource = NetworkUserDataSource(api)

        val user = dataSource.update(
            "u1",
            ProfileUpdate(
                nickname = "Ada",
                age = null,
                gender = null,
                bio = null,
                avatar = FileUpload(
                    bytes = byteArrayOf(1, 2, 3, 4),
                    mimeType = "image/png",
                    filename = "avatar.png",
                ),
            ),
        )

        assertNotNull("an avatar file part is sent", api.lastAvatarPart)
        assertEquals(UPLOADED_AVATAR_URL, user.avatarUrl)
    }

    @Test
    fun `toggleFollow posts to the user's follow endpoint`() = runTest {
        val api = FakeUserApi()
        val dataSource = NetworkUserDataSource(api)

        dataSource.toggleFollow(User(id = "u2", nickname = "Grace", handle = "@grace"))

        assertEquals("u2", api.lastFollowId)
    }

    @Test
    fun `toggleFollow applies the server's follow state and follower count`() = runTest {
        val api = FakeUserApi(
            followResult = FollowToggleDto(isFollowing = true, followerCount = 128401),
        )
        val dataSource = NetworkUserDataSource(api)

        val updated = dataSource.toggleFollow(
            User(id = "u2", nickname = "Grace", handle = "@grace", followerCount = 128400),
        )

        assertTrue(updated.isFollowing)
        assertEquals(128401, updated.followerCount)
    }
}
