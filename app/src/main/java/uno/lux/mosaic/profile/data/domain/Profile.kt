package uno.lux.mosaic.profile.data.domain

import uno.lux.mosaic.user.data.domain.UserId

/** A user's profile metadata. Posts are owned by the screen layer. */
data class Profile(
    val userId: UserId,
    val postsCount: Int,
)
