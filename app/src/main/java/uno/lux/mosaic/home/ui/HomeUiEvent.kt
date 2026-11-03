package uno.lux.mosaic.home.ui

import uno.lux.mosaic.post.domain.PostId
import uno.lux.mosaic.post.ui.report.ReportDialogEvent
import uno.lux.mosaic.user.domain.UserId
import uno.lux.mosaic.video.domain.Video

sealed interface HomeUiEvent {

    data object Refresh : HomeUiEvent

    data object Retry : HomeUiEvent

    data object RefreshErrorShown : HomeUiEvent

    data object FailedActionShown : HomeUiEvent

    data object LoadMore : HomeUiEvent

    data class ToggleLike(
        val postId: PostId,
    ) : HomeUiEvent

    data class ToggleBookmark(
        val postId: PostId,
    ) : HomeUiEvent

    data class Delete(
        val postId: PostId,
    ) : HomeUiEvent

    data class OpenReport(
        val postId: PostId,
    ) : HomeUiEvent

    data class ReportDialog(
        val event: ReportDialogEvent,
    ) : HomeUiEvent

    data object OpenSettings : HomeUiEvent

    data class OpenProfile(
        val userId: UserId,
    ) : HomeUiEvent

    data class OpenPost(
        val postId: PostId,
    ) : HomeUiEvent

    data class OpenVideo(
        val video: Video,
    ) : HomeUiEvent

    data class OpenAlbum(
        val imageUrls: List<String>,
        val initialIndex: Int,
    ) : HomeUiEvent
}
