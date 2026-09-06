package uno.lux.mosaic.comment.data

import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.post.data.domain.PostId

interface CommentDataSource {
    /**
     * One page of [postId]'s thread, newest first. [cursor] is null for the first page and
     * otherwise the token the previous page answered with.
     */
    suspend fun loadComments(postId: PostId, cursor: String?): CommentPage

    suspend fun addComment(postId: PostId, text: String): Comment

    /**
     * Puts the viewer's like on [commentId] into [liked] and answers the state the server settled
     * on — the same contract [uno.lux.mosaic.post.data.PostDataSource.setLike] has, for the same
     * reasons: idempotent, so a retry cannot move the like twice, and carrying only the two
     * fields a like owns, so it has no stale copy of the comment to write back.
     */
    suspend fun setLike(
        postId: PostId,
        commentId: CommentId,
        liked: Boolean,
    ): LikeState
}

/**
 * A window onto a post's thread. [nextCursor] is where the page after this one starts — opaque to
 * everything above [CommentDataSource] — and is null exactly when [hasMore] is false, so a caller
 * holding no cursor is a caller with nothing left to ask for.
 */
data class CommentPage(
    val comments: List<Comment>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
