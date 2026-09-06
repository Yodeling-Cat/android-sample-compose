package uno.lux.mosaic.feed.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface FeedApi {

    @GET("feed")
    suspend fun getFeed(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("include") include: String = "author",
    ): FeedResponse
}
