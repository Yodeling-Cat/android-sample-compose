package uno.lux.mosaic.user.data.network

import kotlinx.serialization.Serializable

/**
 * The sideloaded authors riding along with a page of posts.
 */
@Serializable
data class SideloadedUsers(
    val users: List<UserDto>,
)
