package uno.lux.mosaic.user.data.domain

typealias UserId = String

/**
 * A platform user.
 *
 * [nickname] is the display name; [handle] is the unique `@`-mention.
 *
 * A `null` optional field means the user left it empty.
 */
data class User(
    val id: UserId,
    val nickname: String,
    val handle: String,
    val age: Int? = null,
    val gender: String? = null,
    val location: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val isFollowing: Boolean = false,
)
