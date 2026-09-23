package uno.lux.mosaic.post.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Job
import uno.lux.mosaic.common.util.catchErrors
import uno.lux.mosaic.common.util.launchIfIdle
import kotlin.reflect.KMutableProperty0

/**
 * How far the report the user filled in has got. The dialog owns the reason and the details; this
 * is what says whether they may still be sent, and whether the dialog may close.
 *
 * The dialog stays up for the whole of [SENDING] with Send disabled, so one tap is one report,
 * and closes on [SENT] — the moment the server has taken it, which is what the thanks claims.
 * [FAILED] leaves the dialog exactly as the user filled it in, with Send live again.
 *
 * The failure is a state of its own rather than a return to [IDLE], unlike [CommentSendState]:
 * the snackbar that announces a failed comment would sit behind this dialog's scrim.
 */
enum class ReportSendState {
    IDLE,
    SENDING,
    SENT,
    FAILED,
}

/**
 * Sends the report [block] describes, moving the dialog through the send with [setState] and
 * keeping the [Job] in [jobRef] so [dropReport] can cancel it.
 *
 * Takes a setter so the send can be one field of a larger UI state. Not `launchReporting`: the
 * dialog is still up when the answer arrives, so a failure shows there. [launchIfIdle] backs up
 * the disabled Send button.
 */
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

/**
 * Abandons whatever the dialog was showing, because the dialog is gone. Cancelling matters for a
 * dismissal that lands mid-send: without it the answer to a report nobody is waiting for would
 * still reach [setState], and the *next* dialog would open on the outcome of the last one.
 */
fun dropReport(jobRef: KMutableProperty0<Job?>, setState: (ReportSendState) -> Unit) {
    jobRef.get()?.cancel()
    jobRef.set(null)
    setState(ReportSendState.IDLE)
}
