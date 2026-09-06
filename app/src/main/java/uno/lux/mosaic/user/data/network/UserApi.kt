package uno.lux.mosaic.user.data.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface UserApi {

    @GET("users/{id}")
    suspend fun getUser(
        @Path("id") id: String,
    ): UserResponse

    // Profile edits are sent as multipart/form-data so the avatar can ride along as an
    // uploaded file. Text fields are always present (empty string clears a nullable one);
    // [avatar] is omitted to leave the current photo untouched.
    @Multipart
    @PATCH("users/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Part("nickname") nickname: RequestBody,
        @Part("age") age: RequestBody,
        @Part("gender") gender: RequestBody,
        @Part("bio") bio: RequestBody,
        @Part avatar: MultipartBody.Part?,
    ): UserResponse

    // Toggles whether the current user (the X-User-Id header) follows [id]. The server derives
    // both sides from the header and path, so the request carries no body — as with every other
    // toggle. 403 if [id] is the current user, 404 if [id] is unknown.
    @POST("users/{id}/follow")
    suspend fun toggleFollow(
        @Path("id") id: String,
    ): FollowToggleResponse
}
