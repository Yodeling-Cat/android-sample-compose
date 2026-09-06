package uno.lux.mosaic.comment.data

import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.post.data.domain.PostId

/**
 * Stateless data source for post comments.
 *
 * All three operations return values; the caller ([PostDetailViewModel]) owns the comment list
 * state and applies the returned value. This keeps comments scoped to the screen that needs them
 * and lets the ViewModel be cleared — along with the comment data — the moment the user pops the
 * post detail screen. The thread's paging state is the caller's for the same reason: a cursor kept
 * here would outlive the window it points into.
 */
class CommentRepository(
    private val dataSource: CommentDataSource,
) {
    suspend fun loadComments(postId: PostId, cursor: String?) = dataSource.loadComments(postId, cursor)

    suspend fun addComment(postId: PostId, text: String) = dataSource.addComment(postId, text)

    suspend fun setLike(
        postId: PostId,
        commentId: CommentId,
        liked: Boolean,
    ) = dataSource.setLike(postId, commentId, liked)
}
