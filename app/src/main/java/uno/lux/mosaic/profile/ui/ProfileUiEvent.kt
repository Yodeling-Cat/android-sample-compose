package uno.lux.mosaic.profile.ui

import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.post.data.domain.PostId
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.video.data.domain.Video

sealed interface ProfileUiEvent {

    data object Refresh : ProfileUiEvent

    data object Retry : ProfileUiEvent

    data class ToggleLike(
        val postId: PostId,
    ) : ProfileUiEvent

    data class ToggleBookmark(
        val postId: PostId,
    ) : ProfileUiEvent

    data class Delete(
        val postId: PostId,
    ) : ProfileUiEvent

    data class OpenReport(
        val postId: PostId,
    ) : ProfileUiEvent

    data class SendReport(
        val reason: ReportReason,
        val details: String,
    ) : ProfileUiEvent

    data object CloseReport : ProfileUiEvent

    data object ReportSentShown : ProfileUiEvent

    data object ToggleFollow : ProfileUiEvent

    data object FailedActionShown : ProfileUiEvent

    data object LoadMorePosts : ProfileUiEvent

    data object SavedTabShown : ProfileUiEvent

    data object LoadMoreBookmarks : ProfileUiEvent

    data object LikesTabShown : ProfileUiEvent

    data object LoadMoreLikes : ProfileUiEvent

    data object GoBack : ProfileUiEvent

    data object OpenEditProfile : ProfileUiEvent

    data class OpenPost(
        val postId: PostId,
    ) : ProfileUiEvent

    data class OpenProfile(
        val userId: UserId,
    ) : ProfileUiEvent

    data class OpenVideo(
        val video: Video,
    ) : ProfileUiEvent

    data class OpenAlbum(
        val imageUrls: List<String>,
        val initialIndex: Int,
    ) : ProfileUiEvent

    data class OpenAvatar(
        val avatarUrl: String,
    ) : ProfileUiEvent
}
