package uno.lux.mosaic.post.ui

import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.video.data.domain.Video

/**
 * Everything the detail page can ask of its ViewModel, as one list. The screen holds a single
 * `(PostDetailUiEvent) -> Unit` off [PostDetailUiState.eventSink] instead of one callback per
 * intent, so a page with seventeen of them still takes one parameter.
 *
 * Most of these are things the reader did. The last three are things the *screen* did — it has
 * announced the failure, cleared the composer, made the scroll — and are what spend the signals
 * [PostDetailUiState] describes.
 */
sealed interface PostDetailUiEvent {

    data object GoBack : PostDetailUiEvent

    data class OpenProfile(
        val userId: UserId,
    ) : PostDetailUiEvent

    data class OpenVideo(
        val video: Video,
    ) : PostDetailUiEvent

    data class OpenAlbum(
        val imageUrls: List<String>,
        val initialIndex: Int,
    ) : PostDetailUiEvent

    data object ToggleLike : PostDetailUiEvent

    data object ToggleBookmark : PostDetailUiEvent

    data object Delete : PostDetailUiEvent

    data class Report(
        val reason: ReportReason,
        val details: String,
    ) : PostDetailUiEvent

    /** The report dialog is gone — cancelled, dismissed, or closed on the thanks. */
    data object CloseReport : PostDetailUiEvent

    data object Retry : PostDetailUiEvent

    data class AddComment(
        val text: String,
    ) : PostDetailUiEvent

    data class ToggleCommentLike(
        val commentId: CommentId,
    ) : PostDetailUiEvent

    data object LoadMoreComments : PostDetailUiEvent

    data object RetryComments : PostDetailUiEvent

    data object FailedActionShown : PostDetailUiEvent

    data object CommentSent : PostDetailUiEvent

    data object ScrolledToComment : PostDetailUiEvent
}
