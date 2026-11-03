package uno.lux.mosaic.profile.domain

import uno.lux.mosaic.user.domain.UserId

data class Profile(
    val userId: UserId,
    val postsCount: Int,
)
