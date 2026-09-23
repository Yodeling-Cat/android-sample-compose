package uno.lux.mosaic.post.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.launch
import uno.lux.mosaic.R
import uno.lux.mosaic.app.fixtures.SamplePosts
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.designsystem.components.debouncedClickable
import uno.lux.mosaic.designsystem.components.rememberDebounced
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.post.data.domain.Post
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.ui.Avatar

/**
 * The post's "⋮" button, its bottom sheet of actions, and the dialogs they raise. The feed card
 * and the detail screen's top bar both host it.
 *
 * [onDelete] is null for someone else's post, which hides the delete row, so no separate
 * `canDelete` flag can disagree with it.
 *
 * [reportSend] is the screen's report state. The dialog is modal, so the one report in flight is
 * always this menu's.
 */
@Composable
internal fun PostOverflowMenu(
    post: Post,
    author: User,
    reportSend: ReportSendState,
    onToggleBookmark: () -> Unit,
    onReport: (reason: ReportReason, details: String) -> Unit,
    onReportClosed: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // Saveable so an open sheet or dialog survives the activity recreation a rotation causes.
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showReportDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    IconButton(onClick = ({ showSheet = true }).rememberDebounced(), modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.post_more_options),
            modifier = Modifier.size(22.dp),
        )
    }

    if (showSheet) {
        PostOverflowSheet(
            post = post,
            author = author,
            onDismiss = { showSheet = false },
            onToggleBookmark = onToggleBookmark,
            onReport = { showReportDialog = true },
            onDelete = onDelete?.let { { showDeleteDialog = true } },
        )
    }

    if (showDeleteDialog) {
        DeletePostDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete?.invoke()
            },
        )
    }

    if (showReportDialog) {
        // The thanks says a server has the report, so it waits until one does. A report that
        // failed keeps the dialog, and says so in it, rather than thanking the user for nothing.
        LaunchedEffect(reportSend) {
            if (reportSend != ReportSendState.SENT) return@LaunchedEffect

            context.toast(R.string.report_sent)
            showReportDialog = false
            onReportClosed()
        }

        ReportPostDialog(
            sendState = reportSend,
            onDismiss = {
                showReportDialog = false
                onReportClosed()
            },
            onSubmit = onReport,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostOverflowSheet(
    post: Post,
    author: User,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onReport: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        PostOverflowSheetContent(
            post = post,
            author = author,
            onToggleBookmark = {
                onToggleBookmark()
                dismiss()
            },
            onShare = {
                sharePostLink(context, post)
                dismiss()
            },
            onCopyLink = {
                copyPostLink(context, post)
                dismiss()
            },
            onReport = {
                onReport()
                dismiss()
            },
            // Dismiss first: the confirmation dialog replaces the sheet rather than stacking.
            onDelete = onDelete?.let {
                {
                    dismiss()
                    it()
                }
            },
        )
    }
}

/**
 * What the overflow sheet holds, without the sheet: a [ModalBottomSheet] opens by animating in,
 * so a preview only ever sees it hidden. Each callback is the whole of a row's tap.
 */
@Composable
private fun PostOverflowSheetContent(
    post: Post,
    author: User,
    onToggleBookmark: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
    onReport: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, top = 4.dp, end = 22.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(user = author, size = 38.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    text = author.nickname,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMosaicColors.current.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(4.dp))

        SheetRow(
            iconRes = R.drawable.ic_bookmark_border,
            label = stringResource(
                if (post.isBookmarked) R.string.post_menu_unsave else R.string.post_menu_save,
            ),
            onClick = onToggleBookmark,
        )
        SheetRow(
            iconRes = R.drawable.ic_share,
            label = stringResource(R.string.post_menu_share),
            onClick = onShare,
        )
        SheetRow(
            iconRes = R.drawable.ic_link,
            label = stringResource(R.string.post_menu_copy_link),
            onClick = onCopyLink,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
        )
        SheetRow(
            iconRes = R.drawable.ic_flag,
            label = stringResource(R.string.post_menu_report),
            onClick = onReport,
        )
        if (onDelete != null) {
            SheetRow(
                iconRes = R.drawable.ic_delete,
                label = stringResource(R.string.post_menu_delete),
                danger = true,
                onClick = onDelete,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Shares [Post.url], with the title as the subject for targets that use one (e.g. email). */
private fun sharePostLink(context: Context, post: Post) {
    ShareCompat
        .IntentBuilder(context)
        .setType("text/plain")
        .setSubject(post.title)
        .setText(post.url)
        .setChooserTitle(R.string.post_share_chooser)
        .startChooser()
}

/**
 * Copies the post's link to the clipboard. From Android 13 the system shows its own copy
 * confirmation, so the toast fallback is only needed on older versions.
 */
private fun copyPostLink(context: Context, post: Post) {
    val clipboard = context.getSystemService<ClipboardManager>()!!
    val label = context.getString(R.string.post_link_clip_label)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, post.url))

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        context.toast(R.string.post_link_copied)
    }
}

private fun Context.toast(
    @StringRes messageRes: Int,
) {
    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
}

@Composable
private fun SheetRow(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .debouncedClickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = contentColor)
    }
}

@Preview(name = "Someone else's post")
@Composable
private fun PostOverflowSheetPreview() {
    PostOverflowSheetPreviewContent(canDelete = false)
}

@Preview(name = "Own post")
@Composable
private fun PostOverflowSheetOwnPreview() {
    PostOverflowSheetPreviewContent(canDelete = true)
}

@Composable
private fun PostOverflowSheetPreviewContent(canDelete: Boolean) {
    val post = SamplePosts.first()

    MosaicTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            PostOverflowSheetContent(
                post = post,
                author = SampleUsers.first { it.id == post.authorId },
                onToggleBookmark = {},
                onShare = {},
                onCopyLink = {},
                onReport = {},
                onDelete = if (canDelete) ({}) else null,
            )
        }
    }
}
