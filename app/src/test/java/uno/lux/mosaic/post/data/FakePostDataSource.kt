package uno.lux.mosaic.post.data

import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.data.domain.PostWithUsers
import uno.lux.mosaic.testing.testPostUrl
import uno.lux.mosaic.user.data.domain.User
import java.time.Instant

internal class FakePostDataSource : PostDataSource {

    /**
     * What [fetch] serves, keyed by post ID. An ID that isn't here answers null — the fake's
     * stand-in for the server's 404.
     */
    val fetchable = mutableMapOf<PostId, PostWithUsers>()

    /** IDs passed to [fetch], in call order, so a test can assert a fetch was (or wasn't) made. */
    val fetchedPostIds = mutableListOf<PostId>()

    /** Thrown by [fetch] instead of returning, so tests can drive the failure path. */
    var fetchError: Exception? = null

    override suspend fun fetch(postId: PostId): PostWithUsers? {
        fetchedPostIds += postId
        fetchError?.let { throw it }

        return fetchable[postId]
    }

    /** The draft passed to the most recent [create] call, for test assertions. */
    var lastDraft: NewPost? = null
        private set

    /** Thrown by [create] instead of returning, so tests can drive the failure path. */
    var createError: Exception? = null

    override suspend fun create(draft: NewPost): PostWithUsers {
        lastDraft = draft
        createError?.let { throw it }

        return PostWithUsers(
            post = Post(
                id = "p-new",
                url = testPostUrl("p-new"),
                authorId = "u1",
                title = draft.title,
                body = draft.body,
                createdAt = Instant.EPOCH,
                likeCount = 0,
                commentCount = 0,
            ),
            users = listOf(User(id = "u1", nickname = "Ada", handle = "@u1")),
        )
    }

    /** IDs passed to [delete], in call order, for test assertions. */
    val deletedPostIds = mutableListOf<PostId>()

    /** Thrown by [delete] instead of returning, so tests can drive the failure path. */
    var deleteError: Exception? = null

    override suspend fun delete(postId: PostId) {
        deleteError?.let { throw it }
        deletedPostIds += postId
    }

    /**
     * The like counts this fake's server holds, keyed by post ID.
     *
     * A like request names a post and a state, not a count, so the number in the answer is the
     * server's own — a test asserting on one seeds it here to say what the server started from.
     * An unseeded post counts from zero, which is loud enough to spot when a test meant to seed.
     */
    val likeCounts = mutableMapOf<PostId, Int>()

    /** The `(postId, liked)` pairs passed to [setLike], in call order, for test assertions. */
    val likeRequests = mutableListOf<Pair<PostId, Boolean>>()

    /** Thrown by [setLike] instead of answering, so tests can drive the failure path. */
    var setLikeError: Exception? = null

    /**
     * Runs inside [setLike], [setBookmark] and [report] before any of them answers, which is the
     * only moment a test can see what its caller wrote while the request was out — the optimistic
     * like the repository applied, or the send state the report dialog is reading. Suspending
     * here holds the request open for as long as the test needs it.
     */
    var whileInFlight: (suspend () -> Unit)? = null

    override suspend fun setLike(postId: PostId, liked: Boolean): LikeState {
        likeRequests += postId to liked
        whileInFlight?.invoke()
        setLikeError?.let { throw it }

        val settled = (likeCounts[postId] ?: 0) + if (liked) 1 else -1
        likeCounts[postId] = settled

        return LikeState(isLiked = liked, likeCount = settled)
    }

    /** The `(postId, bookmarked)` pairs passed to [setBookmark], in call order. */
    val bookmarkRequests = mutableListOf<Pair<PostId, Boolean>>()

    /** Thrown by [setBookmark] instead of answering, so tests can drive the failure path. */
    var setBookmarkError: Exception? = null

    override suspend fun setBookmark(postId: PostId, bookmarked: Boolean): Boolean {
        bookmarkRequests += postId to bookmarked
        whileInFlight?.invoke()
        setBookmarkError?.let { throw it }

        return bookmarked
    }

    /** The reports passed to [report], in call order, for test assertions. */
    val reports = mutableListOf<Report>()

    data class Report(
        val postId: PostId,
        val reason: ReportReason,
        val details: String,
    )

    /** Thrown by [report] instead of recording it, so tests can drive the failure path. */
    var reportError: Exception? = null

    override suspend fun report(
        postId: PostId,
        reason: ReportReason,
        details: String,
    ) {
        whileInFlight?.invoke()
        reportError?.let { throw it }
        reports += Report(postId, reason, details)
    }
}
