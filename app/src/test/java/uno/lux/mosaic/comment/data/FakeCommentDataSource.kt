package uno.lux.mosaic.comment.data

import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.user.data.domain.User
import java.time.Instant

internal class FakeCommentDataSource(
    private val currentUser: User,
    /** The whole thread [loadComments] pages over. A `var` so a test can change what a *re*-load brings back. */
    var comments: Map<PostId, List<Comment>> = emptyMap(),
    /** How many comments one page carries — the whole thread by default, so paging is opted into. */
    private val pageSize: Int = Int.MAX_VALUE,
) : CommentDataSource {

    /** Set to make [addComment] fail, for the paths that must not move on an unposted comment. */
    var addError: Throwable? = null

    /** Set to make [loadComments] fail, for the paths that must survive a page that never lands. */
    var loadError: Throwable? = null

    /** The `(postId, cursor)` pairs passed to [loadComments], in call order. */
    val loadRequests = mutableListOf<Pair<PostId, String?>>()

    /**
     * Runs inside [loadComments] before it answers — the one moment a test can act on a thread
     * while a page of it is still on the wire.
     */
    var whileLoading: (suspend () -> Unit)? = null

    /**
     * Serves the [pageSize] comments following [cursor].
     *
     * The token is the id of the page's last comment, which is what the real keyset cursor encodes
     * — close enough that a thread which changed under a reload hands back a *different* token,
     * and that is the case paging has to survive rather than an implementation detail.
     */
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

    /**
     * Runs inside [addComment] before it answers — the one moment a test can read the composer
     * state the ViewModel wrote while the comment is still on the wire.
     */
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

    /**
     * The like counts this fake's server holds, keyed by comment ID.
     *
     * A like request names a comment and a state, not a count, so the number in the answer is the
     * server's own — a test asserting on one seeds it here to say what the server started from.
     */
    val likeCounts = mutableMapOf<CommentId, Int>()

    /** The `(commentId, liked)` pairs passed to [setLike], in call order, for test assertions. */
    val likeRequests = mutableListOf<Pair<CommentId, Boolean>>()

    /** Thrown by [setLike] instead of answering, so tests can drive the failure path. */
    var setLikeError: Exception? = null

    /**
     * Runs inside [setLike] before it answers, which is the only moment a test can see the
     * optimistic value the ViewModel wrote before the request went out.
     */
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
