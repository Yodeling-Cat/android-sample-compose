package uno.lux.mosaic.user.data.network

import kotlinx.serialization.Serializable

/**
 * The sideloaded authors riding along with a page of posts. These are the *same* [UserDto]
 * `GET /users/:id` serves — the API has one user projection on purpose. An identity-only variant
 * would be indistinguishable from a user who has genuinely left the optional fields empty, so
 * ingesting it would blank the bio, identity chips and counts of a profile already loaded in full.
 */
@Serializable
data class SideloadedUsers(
    val users: List<UserDto>,
)
