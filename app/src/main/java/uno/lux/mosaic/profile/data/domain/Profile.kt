package uno.lux.mosaic.profile.data.domain

import uno.lux.mosaic.user.data.domain.UserId

data class Profile(
    val userId: UserId,
    val postsCount: Int,
)
