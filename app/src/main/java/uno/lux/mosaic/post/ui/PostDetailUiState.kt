package uno.lux.mosaic.post.ui

import uno.lux.mosaic.comment.data.domain.Comment
import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.ui.FailedAction
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.user.data.domain.User

enum class CommentSendState {
    IDLE,
    SENDING,
    SENT,
}

data class CommentThread(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
    /** Null both before the first page lands and once the last one has. */
    val nextCursor: String? = null,
    val endReached: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val scrollTo: CommentId? = null,
)

data class PostDetailUiState(
    val content: Content,
    val composerUser: User,
    val commentThread: CommentThread = CommentThread(),
    val commentSend: CommentSendState = CommentSendState.IDLE,
    val reportSend: ReportSendState = ReportSendState.IDLE,
    val failedAction: FailedAction? = null,
) {

    sealed interface Content {
        data object Loading : Content

        data object NotFound : Content

        data class Error(
            val error: AppError,
        ) : Content

        data class Loaded(
            val post: Post,
            val author: User,
            val isOwn: Boolean,
        ) : Content
    }
}
