package uno.lux.mosaic.user.data.network

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val nickname: String,
    val handle: String,
    val age: Int? = null,
    val gender: String? = null,
    val location: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val followerCount: Int,
    val followingCount: Int,
    val isFollowing: Boolean,
)
