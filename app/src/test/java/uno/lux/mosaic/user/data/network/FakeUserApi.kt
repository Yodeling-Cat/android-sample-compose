package uno.lux.mosaic.user.data.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import uno.lux.mosaic.common.data.network.notFoundException

const val UPLOADED_AVATAR_URL = "https://cdn.test/avatars/uploaded.png"

class FakeUserApi(
    private val userById: Map<String, UserDto> = emptyMap(),
    val followResult: FollowToggleDto = FollowToggleDto(isFollowing = true, followerCount = 1),
) : UserApi {

    var getUserError: Exception? = null

    override suspend fun getUser(id: String): UserResponse {
        getUserError?.let { throw it }

        // An unknown id 404s the way the real API does, so the data source's mapping of
        // "gone" to null is exercised rather than sidestepped with a stand-in error.
        val dto = userById[id] ?: throw notFoundException("User '$id' was not found")
        return UserResponse(dto)
    }

    override suspend fun updateUser(
        id: String,
        nickname: RequestBody,
        age: RequestBody,
        gender: RequestBody,
        bio: RequestBody,
        avatar: MultipartBody.Part?,
    ): UserResponse {
        val base = userById[id] ?: error("FakeUserApi: no user for id '$id'")
        lastAvatarPart = avatar

        // Mirror the server: empty text parts clear the nullable fields; a present avatar
        // file yields a fresh hosted URL, its absence leaves the current avatar in place.
        return UserResponse(
            base.copy(
                nickname = nickname.asString(),
                age = age.asString().toIntOrNull(),
                gender = gender.asString().ifEmpty { null },
                bio = bio.asString().ifEmpty { null },
                avatarUrl = if (avatar != null) UPLOADED_AVATAR_URL else base.avatarUrl,
            ),
        )
    }

    var lastAvatarPart: MultipartBody.Part? = null
        private set

    private fun RequestBody.asString(): String = Buffer().also { writeTo(it) }.readUtf8()

    var lastFollowId: String? = null
        private set

    override suspend fun toggleFollow(id: String): FollowToggleResponse {
        lastFollowId = id
        return FollowToggleResponse(followResult)
    }
}

fun userDto(id: String, nickname: String, isFollowing: Boolean = false) = UserDto(
    id = id,
    nickname = nickname,
    handle = "@$id",
    age = null,
    gender = null,
    location = null,
    bio = null,
    followerCount = 0,
    followingCount = 0,
    isFollowing = isFollowing,
)

internal val stubAuthor = userDto("u1", "Ada")
