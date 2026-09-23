package uno.lux.mosaic.user.data.network

import tech.mappie.api.ObjectMappie
import uno.lux.mosaic.user.data.domain.User

object UserMapper : ObjectMappie<UserDto, User>() {
    override fun map(from: UserDto) = mapping()
}
