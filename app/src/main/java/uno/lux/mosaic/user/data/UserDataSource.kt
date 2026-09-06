package uno.lux.mosaic.user.data

import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

interface UserDataSource {
    suspend fun fetch(userId: UserId): User?

    /** Applies [update] to the user's profile and returns the updated [User]. */
    suspend fun update(userId: UserId, update: ProfileUpdate): User

    /**
     * Toggles whether the current user follows [user], returning [user] with the flipped
     * `isFollowing` and the server's new `followerCount`.
     */
    suspend fun toggleFollow(user: User): User
}
