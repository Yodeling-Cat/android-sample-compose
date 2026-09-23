package uno.lux.mosaic.comment.data

import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.post.data.domain.PostId

interface CommentDataSource {
    suspend fun loadComments(postId: PostId, cursor: String?): CommentPage

    suspend fun addComment(postId: PostId, text: String): Comment

    suspend fun setLike(
        postId: PostId,
        commentId: CommentId,
        liked: Boolean,
    ): LikeState
}

/** [nextCursor] is null exactly when [hasMore] is false. */
data class CommentPage(
    val comments: List<Comment>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
