package uno.lux.mosaic.comment.data

import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.post.data.domain.PostId

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
