package uno.lux.mosaic.comment.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uno.lux.mosaic.comment.data.CommentDataSource
import uno.lux.mosaic.comment.data.CommentPage
import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.common.data.network.SetLikeRequestDto
import uno.lux.mosaic.post.data.domain.PostId

class NetworkCommentDataSource(
    private val api: CommentApi,
) : CommentDataSource {

    override suspend fun loadComments(postId: PostId, cursor: String?): CommentPage = withContext(Dispatchers.IO) {
        val response = api.getComments(postId, cursor = cursor)

        CommentPage(
            comments = response.data.map { CommentMapper.map(it) },
            nextCursor = response.page.nextCursor,
            hasMore = response.page.hasMore,
        )
    }

    override suspend fun addComment(postId: PostId, text: String): Comment = withContext(Dispatchers.IO) {
        CommentMapper.map(api.addComment(postId, AddCommentRequestDto(text)).data)
    }

    override suspend fun setLike(
        postId: PostId,
        commentId: CommentId,
        liked: Boolean,
    ): LikeState = withContext(Dispatchers.IO) {
        val result = api.setCommentLike(postId, commentId, SetLikeRequestDto(liked)).data

        LikeState(isLiked = result.isLiked, likeCount = result.likeCount)
    }
}
