package uno.lux.mosaic.user.data.network

import kotlinx.serialization.Serializable

@Serializable
data class SideloadedUsers(
    val users: List<UserDto>,
)
