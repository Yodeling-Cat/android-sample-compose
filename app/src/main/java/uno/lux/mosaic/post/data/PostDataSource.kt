package uno.lux.mosaic.post.data

import uno.lux.mosaic.common.data.LikeState
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.post.data.domain.NewPost
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.post.data.domain.PostWithUsers

interface PostDataSource {
    /**
     * Fetches one post with the users sideloaded beside it, or null when there is no such post. A
     * missing post is an answer (the server's 404), not a failure: a caller has to tell "this post
     * is gone" apart from "the request never got through", and only one of the two is worth
     * retrying.
     */
    suspend fun fetch(postId: PostId): PostWithUsers?

    suspend fun create(draft: NewPost): PostWithUsers

    suspend fun delete(postId: PostId)

    /**
     * Puts the viewer's like on [postId] into [liked] and answers the state the server settled
     * on. Idempotent: asking for a like that is already there changes nothing and answers the
     * same thing, so a retried request — or a second tap the first hasn't answered yet — cannot
     * move the like twice.
     *
     * It takes an ID and a target rather than a [Post] so it has nothing stale to write back:
     * only the two fields it names are the server's to change.
     */
    suspend fun setLike(postId: PostId, liked: Boolean): LikeState

    /** Puts the viewer's bookmark on [postId] into [bookmarked], answering whether it is set. */
    suspend fun setBookmark(postId: PostId, bookmarked: Boolean): Boolean

    /**
     * Reports a post for [reason], with whatever context the reporter typed. [details] is blank
     * when they typed none — the free-text field is optional.
     */
    suspend fun report(
        postId: PostId,
        reason: ReportReason,
        details: String,
    )
}
