package uno.lux.mosaic.user.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uno.lux.mosaic.common.data.network.asPart
import uno.lux.mosaic.common.data.network.asTextPart
import uno.lux.mosaic.common.data.network.notFoundAsNull
import uno.lux.mosaic.user.data.UserDataSource
import uno.lux.mosaic.user.data.domain.ProfileUpdate
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId

class NetworkUserDataSource(
    private val api: UserApi,
) : UserDataSource {

    override suspend fun fetch(userId: UserId): User? = withContext(Dispatchers.IO) {
        // Null for an unknown user is the contract [UserDataSource.fetch] has always declared —
        // the same "gone is an answer" rule the post fetch follows.
        notFoundAsNull { api.getUser(userId) }?.data?.let(UserMapper::map)
    }

    override suspend fun update(userId: UserId, update: ProfileUpdate): User =
        withContext(Dispatchers.IO) {
            // Empty text parts clear the nullable fields server-side (multipart can't carry a
            // JSON null); the avatar part is sent only when a new image was chosen.
            api
                .updateUser(
                    id = userId,
                    nickname = update.nickname.asTextPart(),
                    age = update.age
                        ?.toString()
                        .orEmpty()
                        .asTextPart(),
                    gender = update.gender
                        ?.let(GenderDtoMapper::map)
                        ?.wireName
                        .orEmpty()
                        .asTextPart(),
                    bio = update.bio.orEmpty().asTextPart(),
                    avatar = update.avatar?.asPart("avatar"),
                ).data
                .let(UserMapper::map)
        }

    override suspend fun toggleFollow(user: User): User = withContext(Dispatchers.IO) {
        val result = api.toggleFollow(user.id).data
        user.copy(isFollowing = result.isFollowing, followerCount = result.followerCount)
    }
}
