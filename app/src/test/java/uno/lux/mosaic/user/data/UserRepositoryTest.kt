package uno.lux.mosaic.user.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uno.lux.mosaic.common.data.files.FileUpload
import uno.lux.mosaic.user.data.domain.Gender
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User

class UserRepositoryTest {

    private fun repository(vararg fetchResults: User = emptyArray()) =
        UserRepository(FakeUserDataSource(fetchResults.associateBy { it.id }))

    @Test
    fun `users starts empty`() = runTest {
        assertTrue(repository().users.first().isEmpty())
    }

    @Test
    fun `user returns null for an unknown id`() = runTest {
        assertNull(repository().user("nobody").first())
    }

    @Test
    fun `ingest populates the user cache`() = runTest {
        val repo = repository()
        repo.ingest(listOf(user("u1", "Ada"), user("u2", "Grace")))

        val cache = repo.users.first()
        assertEquals("Ada", cache["u1"]?.nickname)
        assertEquals("Grace", cache["u2"]?.nickname)
    }

    @Test
    fun `ingest merges with existing entries, keeping both`() = runTest {
        val repo = repository()
        repo.ingest(listOf(user("u1", "Ada")))
        repo.ingest(listOf(user("u2", "Grace")))

        assertEquals(2, repo.users.first().size)
    }

    @Test
    fun `ingest overwrites an existing entry when the same id arrives`() = runTest {
        val repo = repository()
        repo.ingest(listOf(user("u1", "Ada")))
        repo.ingest(listOf(user("u1", "Ada Lovelace")))

        assertEquals("Ada Lovelace", repo.users.first()["u1"]?.nickname)
    }

    @Test
    fun `refresh fetches a user and adds them to the cache`() = runTest {
        val repo = repository(user("u1", "Ada"))

        repo.refresh("u1")

        assertEquals("Ada", repo.user("u1").first()?.nickname)
    }

    @Test
    fun `updateProfile persists the update and replaces the cached entry`() = runTest {
        val repo = repository(user("u1", "Ada"))
        repo.refresh("u1")

        repo.updateProfile("u1", profileUpdate(nickname = "Ada King", bio = "Countess"))

        val updated = repo.user("u1").first()!!
        assertEquals("Ada King", updated.nickname)
        assertEquals("Countess", updated.bio)
    }

    @Test
    fun `updateProfile sends the update to the data source`() = runTest {
        val dataSource = FakeUserDataSource(mapOf("u1" to user("u1", "Ada")))
        val repo = UserRepository(dataSource)

        val update = profileUpdate(
            nickname = "Ada King",
            avatar = FileUpload(byteArrayOf(1, 2, 3), "image/png", "avatar.png"),
        )
        repo.updateProfile("u1", update)

        assertEquals("u1" to update, dataSource.lastUpdate)
    }

    @Test
    fun `updateProfile reflects the uploaded avatar url in the cache`() = runTest {
        val dataSource = FakeUserDataSource(mapOf("u1" to user("u1", "Ada")))
        val repo = UserRepository(dataSource)
        repo.refresh("u1")

        repo.updateProfile(
            "u1",
            profileUpdate(
                nickname = "Ada",
                avatar = FileUpload(byteArrayOf(9), "image/png", "avatar.png"),
            ),
        )

        assertEquals(FakeUserDataSource.UPLOADED_AVATAR_URL, repo.user("u1").first()?.avatarUrl)
    }

    @Test
    fun `toggleFollow flips the cached follow state and follower count`() = runTest {
        val repo = repository()
        repo.ingest(listOf(User(id = "u1", nickname = "Ada", handle = "@ada", followerCount = 10)))

        repo.toggleFollow("u1")

        val followed = repo.user("u1").first()!!
        assertTrue(followed.isFollowing)
        assertEquals(11, followed.followerCount)
    }

    @Test
    fun `toggleFollow sends the toggle to the data source`() = runTest {
        val dataSource = FakeUserDataSource(mapOf("u1" to user("u1", "Ada")))
        val repo = UserRepository(dataSource)
        repo.ingest(listOf(user("u1", "Ada")))

        repo.toggleFollow("u1")

        assertEquals("u1", dataSource.lastFollowToggle)
    }

    @Test
    fun `toggleFollow is a no-op for an uncached user`() = runTest {
        val dataSource = FakeUserDataSource(mapOf("u1" to user("u1", "Ada")))
        val repo = UserRepository(dataSource)

        repo.toggleFollow("u1")

        assertNull(dataSource.lastFollowToggle)
        assertNull(repo.user("u1").first())
    }

    @Test
    fun `user flow emits the user after a subsequent ingest`() = runTest {
        val repo = repository()
        assertNull(repo.user("u1").first())

        repo.ingest(listOf(user("u1", "Ada")))

        assertEquals("Ada", repo.user("u1").first()?.nickname)
    }
}

private fun user(id: String, nickname: String) = User(id = id, nickname = nickname, handle = "@$id")

private fun profileUpdate(
    nickname: String,
    age: Int? = null,
    gender: Gender? = null,
    bio: String? = null,
    avatar: FileUpload? = null,
) = ProfileUpdate(
    nickname = nickname,
    age = age,
    gender = gender,
    bio = bio,
    avatar = avatar,
)
