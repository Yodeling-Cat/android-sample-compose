package uno.lux.mosaic.post.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uno.lux.mosaic.app.fixtures.SamplePosts
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.video.data.domain.Video

private const val FEED_BODY_MAX_LINES = 5

@Composable
internal fun PostCard(
    post: Post,
    author: User,
    reportSend: ReportSendState,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenVideo: (Video) -> Unit,
    onOpenAlbum: (List<String>, initialIndex: Int) -> Unit,
    onOpenPost: () -> Unit,
    onReport: (reason: ReportReason, details: String) -> Unit,
    onReportClosed: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(16.dp),
            ),
    ) {
        PostAuthorHeader(
            post = post,
            author = author,
            onOpenProfile = onOpenProfile,
            // The avatar + identity open the author's profile; the overflow stays its own target.
            trailing = {
                PostOverflowMenu(
                    post = post,
                    author = author,
                    reportSend = reportSend,
                    onToggleBookmark = onToggleBookmark,
                    onReport = onReport,
                    onReportClosed = onReportClosed,
                    onDelete = onDelete,
                )
            },
        )
        PostBody(
            title = post.title,
            body = post.body,
            maxBodyLines = FEED_BODY_MAX_LINES,
            onClick = onOpenPost,
        )
        PostMedia(post = post, onOpenVideo = onOpenVideo, onOpenAlbum = onOpenAlbum)
        PostActions(
            post = post,
            onToggleLike = onToggleLike,
            onToggleBookmark = onToggleBookmark,
            onCommentClick = onOpenPost,
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Preview
@Composable
private fun PostCardPreview() {
    val users = SampleUsers.associateBy { it.id }
    val post = SamplePosts.first()

    MosaicTheme {
        PostCard(
            post = post,
            author = users.getValue(post.authorId),
            reportSend = ReportSendState.IDLE,
            onToggleLike = {},
            onToggleBookmark = {},
            onOpenProfile = {},
            onOpenVideo = {},
            onOpenAlbum = { _, _ -> },
            onOpenPost = {},
            onReport = { _, _ -> },
            onReportClosed = {},
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}
