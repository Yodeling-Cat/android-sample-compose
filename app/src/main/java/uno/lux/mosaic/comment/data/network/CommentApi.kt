package uno.lux.mosaic.comment.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import uno.lux.mosaic.common.data.network.LikeStateResponse
import uno.lux.mosaic.common.data.network.SetLikeRequestDto

interface CommentApi {

    @GET("posts/{id}/comments")
    suspend fun getComments(
        @Path("id") postId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): CommentListResponse

    @POST("posts/{id}/comments")
    suspend fun addComment(
        @Path("id") postId: String,
        @Body body: AddCommentRequestDto,
    ): CommentResponse

    // Puts the current user's (the X-User-Id header) like on [commentId] into the requested
    // state, and is a PUT for the same reason [uno.lux.sample.post.data.network.PostApi.setLike]
    // is: a repeat has to be harmless.
    @PUT("posts/{id}/comments/{commentId}/like")
    suspend fun setCommentLike(
        @Path("id") postId: String,
        @Path("commentId") commentId: String,
        @Body body: SetLikeRequestDto,
    ): LikeStateResponse
}
