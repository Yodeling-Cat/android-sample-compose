package uno.lux.mosaic.user.data.domain

typealias UserId = String

/**
 * A platform user.
 *
 * [nickname] is the display name; [handle] is the unique `@`-mention. [avatarUrl] is a URI
 * reference to the profile photo; `null` falls back to the generated initials avatar.
 *
 * A `null` optional field means the user left it empty — never "not loaded yet". Every user
 * the API serves, embedded authors included, carries the same full projection, so there is no
 * partial [User] in flight that a cache write could mistake for an empty one.
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
