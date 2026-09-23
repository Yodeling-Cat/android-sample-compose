package uno.lux.mosaic.user.data.network

import uno.lux.mosaic.user.data.domain.User

// TODO: Why do we hand write this?

/**
 * Handwritten, not Mappie: [UserDto] and [User] are field-identical, so there is nothing to map.
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
