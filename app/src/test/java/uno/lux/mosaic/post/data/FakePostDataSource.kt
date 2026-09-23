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

    val fetchable = mutableMapOf<PostId, PostWithUsers>()

    val fetchedPostIds = mutableListOf<PostId>()

    var fetchError: Exception? = null

    override suspend fun fetch(postId: PostId): PostWithUsers? {
        fetchedPostIds += postId
        fetchError?.let { throw it }

        return fetchable[postId]
    }

    var lastDraft: NewPost? = null
        private set

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

    val deletedPostIds = mutableListOf<PostId>()

    var deleteError: Exception? = null

    override suspend fun delete(postId: PostId) {
        deleteError?.let { throw it }
        deletedPostIds += postId
    }

    val likeCounts = mutableMapOf<PostId, Int>()

    val likeRequests = mutableListOf<Pair<PostId, Boolean>>()

    var setLikeError: Exception? = null

    var whileInFlight: (suspend () -> Unit)? = null

    override suspend fun setLike(postId: PostId, liked: Boolean): LikeState {
        likeRequests += postId to liked
        whileInFlight?.invoke()
        setLikeError?.let { throw it }

        val settled = (likeCounts[postId] ?: 0) + if (liked) 1 else -1
        likeCounts[postId] = settled

        return LikeState(isLiked = liked, likeCount = settled)
    }

    val bookmarkRequests = mutableListOf<Pair<PostId, Boolean>>()

    var setBookmarkError: Exception? = null

    override suspend fun setBookmark(postId: PostId, bookmarked: Boolean): Boolean {
        bookmarkRequests += postId to bookmarked
        whileInFlight?.invoke()
        setBookmarkError?.let { throw it }

        return bookmarked
    }

    val reports = mutableListOf<Report>()

    data class Report(
        val postId: PostId,
        val reason: ReportReason,
        val details: String,
    )

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
