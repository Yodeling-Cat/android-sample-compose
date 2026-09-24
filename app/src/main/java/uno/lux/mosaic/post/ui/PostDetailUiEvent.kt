package uno.lux.mosaic.post.ui

import uno.lux.mosaic.comment.data.domain.CommentId
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.video.data.domain.Video

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

    data object OpenReport : PostDetailUiEvent

    data class SendReport(
        val reason: ReportReason,
        val details: String,
    ) : PostDetailUiEvent

    data object CloseReport : PostDetailUiEvent

    data object ReportSentShown : PostDetailUiEvent

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
