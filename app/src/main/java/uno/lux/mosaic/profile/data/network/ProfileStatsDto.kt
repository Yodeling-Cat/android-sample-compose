package uno.lux.mosaic.profile.data.network

import kotlinx.serialization.Serializable

@Serializable
data class ProfileStatsDto(
    val postsCount: Int,
)
