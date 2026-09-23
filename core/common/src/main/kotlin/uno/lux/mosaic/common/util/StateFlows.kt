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

private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

fun <T> Flow<T>.stateInWhileSubscribed(scope: CoroutineScope, initialValue: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS), initialValue)

/** Returns [Unit], not the [Job], so an action can be an expression body. */
fun ViewModel.launch(block: suspend () -> Unit) {
    viewModelScope.launch { block() }
}

/** Sharing [jobRef] with the initial load makes a refresh and a retry exclude each other. */
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

fun ViewModel.launchIfIdle(jobRef: KMutableProperty0<Job?>, block: suspend () -> Unit) {
    if (jobRef.get()?.isActive == true) return
    jobRef.set(viewModelScope.launch { block() })
}

/**
 * Only for a mutation whose failure shows where the tap happened, such as an optimistic like.
 * Otherwise use `launchReporting`, or the failure looks like success.
 */
fun ViewModel.launchCatching(block: suspend () -> Unit) {
    viewModelScope.launch { catchErrors(block = block) }
}

/** Always logs the failure, whether or not [onError] is given. */
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
