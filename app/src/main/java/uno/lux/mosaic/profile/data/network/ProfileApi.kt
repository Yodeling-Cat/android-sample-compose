package uno.lux.mosaic.profile.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ProfileApi {

    @GET("users/{id}/profile")
    suspend fun getProfileStats(
        @Path("id") id: String,
    ): ProfileStatsResponse

    @GET("users/{id}/posts")
    suspend fun getUserPosts(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): PostsWithAuthorsResponse

    @GET("users/{id}/bookmarks")
    suspend fun getBookmarks(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): PostsWithAuthorsResponse

    @GET("users/{id}/likes")
    suspend fun getLikes(
        @Path("id") id: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): PostsWithAuthorsResponse
}
