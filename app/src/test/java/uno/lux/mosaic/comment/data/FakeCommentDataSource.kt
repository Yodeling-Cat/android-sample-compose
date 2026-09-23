package uno.lux.mosaic.comment.data

import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.user.data.domain.User
import java.time.Instant

internal class FakeCommentDataSource(
    private val currentUser: User,
    var comments: Map<PostId, List<Comment>> = emptyMap(),
    private val pageSize: Int = Int.MAX_VALUE,
) : CommentDataSource {

    var addError: Throwable? = null

    var loadError: Throwable? = null

    val loadRequests = mutableListOf<Pair<PostId, String?>>()

    var whileLoading: (suspend () -> Unit)? = null

    override suspend fun loadComments(postId: PostId, cursor: String?): CommentPage {
        loadRequests += postId to cursor
        whileLoading?.invoke()
        loadError?.let { throw it }

        val thread = comments[postId].orEmpty()
        val from = cursor?.let { token -> thread.indexOfFirst { it.id == token } + 1 } ?: 0
        val window = thread.drop(from).take(pageSize)
        val hasMore = from + window.size < thread.size

        return CommentPage(
            comments = window,
            nextCursor = if (hasMore) window.last().id else null,
            hasMore = hasMore,
        )
    }

    var whileAdding: (suspend () -> Unit)? = null

    override suspend fun addComment(postId: PostId, text: String): Comment {
        whileAdding?.invoke()
        addError?.let { throw it }

        return Comment(
            id = "local-new",
            author = currentUser,
            createdAt = Instant.EPOCH,
            text = text,
            likeCount = 0,
        )
    }

    val likeCounts = mutableMapOf<CommentId, Int>()

    val likeRequests = mutableListOf<Pair<CommentId, Boolean>>()

    var setLikeError: Exception? = null

    var whileInFlight: (suspend () -> Unit)? = null

    override suspend fun setLike(
        postId: PostId,
        commentId: CommentId,
        liked: Boolean,
    ): LikeState {
        likeRequests += commentId to liked
        whileInFlight?.invoke()
        setLikeError?.let { throw it }

        val settled = (likeCounts[commentId] ?: 0) + if (liked) 1 else -1
        likeCounts[commentId] = settled

        return LikeState(isLiked = liked, likeCount = settled)
    }
}
