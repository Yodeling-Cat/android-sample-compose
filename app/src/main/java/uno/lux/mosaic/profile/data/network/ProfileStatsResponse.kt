package uno.lux.mosaic.profile.data.network

import kotlinx.serialization.Serializable

@Serializable
data class ProfileStatsResponse(
    val data: ProfileStatsDto,
)
