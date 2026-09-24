package uno.lux.mosaic.post.ui

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uno.lux.mosaic.common.data.ReportReason
import uno.lux.mosaic.common.util.catchErrors
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.domain.PostId

/**
 * Unlike [CommentSendState], a failure is its own state: a snackbar would sit behind the dialog's
 * scrim.
 */
enum class ReportSendState {
    IDLE,
    SENDING,
    FAILED,
}

/** `null` when there is neither a dialog up nor a thanks still to show. */
@Immutable
sealed interface PostReport {
    data class Open(
        val postId: PostId,
        val send: ReportSendState = ReportSendState.IDLE,
    ) : PostReport

    /** The server has the report and the dialog is closed; the thanks is still to be shown. */
    data object Sent : PostReport
}

class PostReporter(
    private val scope: CoroutineScope,
    private val postRepository: PostRepository,
) {
    private val _report = MutableStateFlow<PostReport?>(null)
    val report: StateFlow<PostReport?> = _report.asStateFlow()

    private var sendJob: Job? = null

    fun open(postId: PostId) {
        close()
        _report.value = PostReport.Open(postId)
    }

    /** A no-op with no dialog open, or while a send is already out. */
    fun send(reason: ReportReason, details: String) {
        val open = _report.value as? PostReport.Open ?: return
        if (sendJob?.isActive == true) return

        sendJob = scope.launch {
            setSend(ReportSendState.SENDING)

            catchErrors(onError = { setSend(ReportSendState.FAILED) }) {
                postRepository.report(open.postId, reason, details)
                _report.value = PostReport.Sent
            }
        }
    }

    /** Cancels a send still out, so its answer cannot land after the dialog has gone. */
    fun close() {
        sendJob?.cancel()
        sendJob = null
        _report.value = null
    }

    fun sentShown() = _report.update { if (it == PostReport.Sent) null else it }

    private fun setSend(state: ReportSendState) = _report.update {
        if (it is PostReport.Open) it.copy(send = state) else it
    }
}
