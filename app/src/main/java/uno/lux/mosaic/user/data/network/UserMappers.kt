package uno.lux.mosaic.user.data.network

import uno.lux.mosaic.user.data.domain.User

// TODO: Why do we hand write this?

/**
 * Handwritten rather than a Mappie object because [UserDto] and [User] are field-identical:
 * there is nothing for a mapper to resolve, and the explicit copy documents the projection.
 */
internal fun UserDto.toDomain() = User(
    id = id,
    nickname = nickname,
    handle = handle,
    age = age,
    gender = gender,
    location = location,
    bio = bio,
    avatarUrl = avatarUrl,
    followerCount = followerCount,
    followingCount = followingCount,
    isFollowing = isFollowing,
)
