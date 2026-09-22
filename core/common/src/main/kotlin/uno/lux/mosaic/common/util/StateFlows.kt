package uno.lux.mosaic.common.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.reflect.KMutableProperty0

/** Grace period the upstream stays active after the last subscriber leaves (e.g. a config change). */
private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

/**
 * Shares this [Flow] as a [StateFlow] in [scope], started
 * [WhileSubscribed][SharingStarted.WhileSubscribed] with a brief grace period — the standard
 * policy for exposing UI state from a ViewModel.
 */
fun <T> Flow<T>.stateInWhileSubscribed(scope: CoroutineScope, initialValue: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), initialValue)

/**
 * Launches [block] in [viewModelScope] through [launchIfIdle], setting [refreshing] to `true` for
 * its duration. Keeps the pull-to-refresh bookkeeping out of ViewModel bodies.
 *
 * Passing the same [jobRef] a screen's initial load uses is what makes a pull-to-refresh and a
 * retry exclude each other: both fetch the same thing, so whichever arrives second would only
 * duplicate the request. A refresh dropped this way leaves [refreshing] untouched — the load
 * already running owns the indicator.
 */
fun ViewModel.launchRefresh(
    jobRef: KMutableProperty0<Job?>,
    refreshing: MutableStateFlow<Boolean>,
    block: suspend () -> Unit,
) {
    launchIfIdle(jobRef) {
        refreshing.value = true
        try {
            block()
        } finally {
            refreshing.value = false
        }
    }
}

/**
 * Launches [block] in [viewModelScope], writing the resulting [Job] into [jobRef], but only if
 * the previous job stored there is no longer active. Subsequent calls while the job is running
 * are silently ignored, preventing duplicate in-flight requests (e.g. repeated load-more
 * triggers before the first page completes).
 */
fun ViewModel.launchIfIdle(jobRef: KMutableProperty0<Job?>, block: suspend () -> Unit) {
    if (jobRef.get()?.isActive == true) return
    jobRef.set(viewModelScope.launch { block() })
}

/**
 * Launches [block] in [viewModelScope], logging and discarding any failure ([catchErrors] does
 * both), where the alternative is spelling out `launch { ignoreErrors { … } }` at every call site.
 *
 * Only for a mutation whose failure is already visible where the tap happened — an optimistic
 * like or bookmark, which reverts the control the user just touched. A mutation whose UI is gone
 * by the time the server answers must name its failure instead, or the tap looks exactly like
 * success; `common/ui`'s `launchReporting` is that shape.
 *
 * Returns [Unit] rather than the [Job], as [launchRefresh] and [launchIfIdle] do, so an action
 * declared to return [Unit] can be written as an expression body.
 */
fun ViewModel.launchCatching(block: suspend () -> Unit) {
    viewModelScope.launch { catchErrors(block = block) }
}

/**
 * Runs [block], discarding any non-[CancellationException] thrown. Calls [onError] before
 * discarding so callers can update error state without repeating the rethrow boilerplate.
 *
 * The failure is always logged, whether or not [onError] is given — a swallowed exception that
 * left no trace is the one failure mode this function must not have, and leaving the logging to
 * each caller means the callers with nothing else to do about the error are the ones that go
 * quiet.
 */
suspend fun catchErrors(onError: (Exception) -> Unit = {}, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e)
        onError(e)
    }
}
