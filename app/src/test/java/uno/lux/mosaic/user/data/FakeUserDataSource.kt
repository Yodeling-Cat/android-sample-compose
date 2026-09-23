package uno.lux.mosaic.user.data

import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId
import java.net.ConnectException

internal class FakeUserDataSource(
    private val users: Map<UserId, User> = emptyMap(),
    var failUpdates: Boolean = false,
) : UserDataSource {

    var lastUpdate: Pair<UserId, ProfileUpdate>? = null
        private set

    var lastFollowToggle: UserId? = null
        private set

    override suspend fun fetch(userId: UserId): User? = users[userId]

    override suspend fun update(userId: UserId, update: ProfileUpdate): User {
        if (failUpdates) throw ConnectException("FakeUserDataSource: update failed")
        lastUpdate = userId to update

        val base = users[userId] ?: error("FakeUserDataSource: no user for id '$userId'")
        return base.copy(
            nickname = update.nickname,
            age = update.age,
            gender = update.gender,
            bio = update.bio,
            // A new avatar upload yields a fresh hosted URL; no upload leaves it unchanged.
            avatarUrl = if (update.avatar != null) UPLOADED_AVATAR_URL else base.avatarUrl,
        )
    }

    var toggleFollowError: Exception? = null

    override suspend fun toggleFollow(user: User): User {
        lastFollowToggle = user.id
        toggleFollowError?.let { throw it }

        // Mirror the server: flip the follow state and adjust the follower count to match.
        val nowFollowing = !user.isFollowing
        return user.copy(
            isFollowing = nowFollowing,
            followerCount = user.followerCount + if (nowFollowing) 1 else -1,
        )
    }

    companion object {
        const val UPLOADED_AVATAR_URL = "https://cdn.test/avatars/uploaded.png"
    }
}
