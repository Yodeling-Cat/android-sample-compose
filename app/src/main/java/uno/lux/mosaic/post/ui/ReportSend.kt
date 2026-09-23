package uno.lux.mosaic.post.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Job
import uno.lux.mosaic.common.util.catchErrors
import uno.lux.mosaic.common.util.launchIfIdle
import uno.lux.mosaic.post.data.domain.PostId
import kotlin.reflect.KMutableProperty0

/**
 * Unlike [CommentSendState], a failure is its own state: a snackbar would sit behind the dialog's
 * scrim.
 */
enum class ReportSendState {
    IDLE,
    SENDING,
    SENT,
    FAILED,
}

/** The send of a report on one post in a list; `null` means no report is in flight. */
data class PostReportSend(
    val postId: PostId,
    val state: ReportSendState,
)

/**
 * Every post but the reported one reads [ReportSendState.IDLE], so a change to the send state
 * reaches only the card whose dialog is waiting for it.
 */
fun PostReportSend?.sendStateFor(postId: PostId): ReportSendState =
    if (this != null && this.postId == postId) state else ReportSendState.IDLE

fun ViewModel.launchReport(
    jobRef: KMutableProperty0<Job?>,
    setState: (ReportSendState) -> Unit,
    block: suspend () -> Unit,
) = launchIfIdle(jobRef) {
    setState(ReportSendState.SENDING)

    catchErrors(onError = { setState(ReportSendState.FAILED) }) {
        block()
        setState(ReportSendState.SENT)
    }
}

// TODO: Having to do this cleanup sounds like bad architecture, there's global state somewhere, that shouldn't exist.
// TODO: It shouldn't be in the post's ui package

/** Cancels the send, so an answer nobody is waiting for cannot reach the next dialog. */
fun dropReport(jobRef: KMutableProperty0<Job?>, setState: (ReportSendState) -> Unit) {
    jobRef.get()?.cancel()
    jobRef.set(null)
    setState(ReportSendState.IDLE)
}
