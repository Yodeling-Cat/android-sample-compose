package uno.lux.mosaic.user.data.network

import kotlinx.serialization.Serializable

@Serializable
data class FollowToggleDto(
    val isFollowing: Boolean,
    val followerCount: Int,
)
