package uno.lux.mosaic.common.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uno.lux.mosaic.common.asText
import uno.lux.mosaic.common.util.catchErrors
import uno.lux.mosaic.common.util.launchCatching

// TODO: Using a global enum for this is terrible architecture. Should probably take any data class as argument instead.

/**
 * A fire-and-forget user action whose request failed after the UI had already moved on — the
 * confirmation dialog closed, the sheet dismissed, the follow button never moved — so there is
 * nothing left on screen for the failure to show up in. The ViewModel that ran the action names it here for the screen
 * to announce once and then spend.
 *
 * Two kinds of mutation are deliberately absent. An optimistic one — a like or a bookmark —
 * reverts the control the user just tapped, and that revert is its own announcement. A report
 * keeps its dialog up until the server answers, so the failure is stated in the dialog the user
 * is still reading; a snackbar would land behind it either way. The rule is whether the failure
 * is visible where the tap happened, not which screen the action belongs to.
 */
enum class FailedAction {
    DELETE_POST,
    FOLLOW,
    SEND_COMMENT,
}

/**
 * Launches [block], handing [action] to [onFailed] if it fails. [launchCatching] with somewhere
 * to report to — for the mutations whose UI is gone by the time the answer arrives, where logging
 * and discarding would leave the tap looking exactly like success.
 *
 * Takes a setter rather than the [MutableStateFlow] itself, so it says nothing about where the
 * ViewModel keeps the announcement: a flow of its own, or one field of a larger UI state that is
 * copied. The ViewModel still owns the state a test asserts against, which is the property
 * [uno.lux.mosaic.common.util.launchRefresh] takes its `refreshing` flag for. Not in `app/util`
 * with the other launch shapes because it names [FailedAction], and `app/util` imports nothing
 * of the project's — the same reason [catchErrors]'s error-sink overload lives here.
 */
fun ViewModel.launchReporting(
    action: FailedAction,
    onFailed: (FailedAction) -> Unit,
    block: suspend () -> Unit,
) {
    viewModelScope.launch {
        catchErrors(onError = { onFailed(action) }, block = block)
    }
}

/**
 * Announces [failedAction] in [snackbarHostState] once, then calls [onShown] to spend it. The
 * spend is what stops a configuration change from repeating a failure the user has already read:
 * the composition is rebuilt from nothing, so a signal still in the state would be announced
 * again on every rotation.
 *
 * No retry action, unlike a failed load: the thing to retry is a tap that is one gesture away,
 * and the control is still on screen.
 */
@Composable
fun FailedActionEffect(
    failedAction: FailedAction?,
    snackbarHostState: SnackbarHostState,
    onShown: () -> Unit,
) {
    // Resolved out here because asText() is itself @Composable, and keyed on the message so a
    // locale change mid-snackbar re-announces in the language the user just picked.
    val message = failedAction?.asText()

    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect

        snackbarHostState.showSnackbar(message)
        onShown()
    }
}
