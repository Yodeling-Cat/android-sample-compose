package uno.lux.mosaic.post.ui

import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.user.data.domain.User

/**
 * How far the comment in the composer has got. The composer owns the text itself, so this is
 * what tells it when to give the text up: [SENT] is reached only once the server has taken the
 * comment, and a failure returns to [IDLE] with what the user typed still in the box.
 *
 * [SENDING] disables the field and the send button, so one tap is one comment.
 */
enum class CommentSendState {
    IDLE,
    SENDING,
    SENT,
}

/**
 * The stretch of the thread this page has loaded, together with where the next page starts and
 * how the last load went. One value rather than a field per part because they move together, so
 * the screen can never see a thread grown past a cursor that has not.
 *
 * [nextCursor] is the one part the screen never draws. It lives here anyway, because *that* is
 * the invariant: a cursor kept anywhere else could be updated a moment after the list it points
 * into.
 */
data class CommentThread(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
    /** Null both before the first page lands and once the last one has. */
    val nextCursor: String? = null,
    val endReached: Boolean = false,
    /** The last attempt at the page after [comments] failed, so the footer offers a retry. */
    val loadMoreFailed: Boolean = false,
    /** The comment the screen still owes a scroll to, or null once it has made it. */
    val scrollTo: CommentId? = null,
)

/**
 * The whole of what the detail page draws, as one value.
 *
 * [content] is the page's one genuinely exclusive axis: it is either waiting for the post,
 * showing it, or explaining why it cannot. Everything beside it is *orthogonal* to that, and so
 * is a field rather than a state of its own — a thread can be loading under a post that is
 * already up. Modelling those as further [Content] cases would multiply out into a case per
 * combination.
 *
 * The thread is deliberately not inside [Content.Loaded], although that is where it is drawn.
 * The two are fetched in parallel on a cold start, so a first page can land while the post has
 * not; with nowhere else to put it, that page would be dropped.
 *
 * [failedAction], [scrollTo][CommentThread.scrollTo] and [commentSend] are signals rather than
 * descriptions, spent by sending [PostDetailUiEvent.FailedActionShown],
 * [PostDetailUiEvent.ScrolledToComment] and [PostDetailUiEvent.CommentSent] back once done.
 * State with a spend, which a rotation cannot replay and a test can assert on directly.
 */
data class PostDetailUiState(
    val content: Content,
    /** The signed-in user, drawn as the composer's avatar. */
    val composerUser: User,
    val commentThread: CommentThread = CommentThread(),
    val commentSend: CommentSendState = CommentSendState.IDLE,
    val reportSend: ReportSendState = ReportSendState.IDLE,
    val failedAction: FailedAction? = null,
) {

    sealed interface Content {
        data object Loading : Content

        /** The server answered that there is no such post — it never existed, or it was deleted. */
        data object NotFound : Content

        /**
         * The post could not be fetched. Distinct from [NotFound]: nothing is known about the post
         * yet, so the screen offers a retry rather than telling the user it is gone.
         */
        data class Error(
            val error: AppError,
        ) : Content

        /** [isOwn] is the post being authored by the signed-in user; it gates the delete action. */
        data class Loaded(
            val post: Post,
            val author: User,
            val isOwn: Boolean,
        ) : Content
    }
}
