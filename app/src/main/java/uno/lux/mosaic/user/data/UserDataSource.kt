package uno.lux.mosaic.user.data

import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

interface UserDataSource {
    suspend fun fetch(userId: UserId): User?

    suspend fun update(userId: UserId, update: ProfileUpdate): User

    suspend fun toggleFollow(user: User): User
}
