package uno.lux.mosaic.common.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import uno.lux.mosaic.common.asText
import uno.lux.mosaic.common.util.catchErrors
import uno.lux.mosaic.common.util.launchCatching

// TODO: Using a global enum for this is terrible architecture. Should probably take any data class as argument instead.

/**
 * A fire-and-forget action whose request failed after its UI was gone (the dialog closed, the
 * sheet dismissed), so the screen announces it once and then spends it.
 *
 * Optimistic mutations and reports are absent on purpose: a like reverts the control the user
 * tapped, and a report's dialog stays up to show its own failure. The rule is whether the failure
 * is visible where the tap happened.
 */
enum class FailedAction {
    DELETE_POST,
    FOLLOW,
    SEND_COMMENT,
}

/**
 * [launchCatching] that hands [action] to [onFailed] on failure, for a mutation whose UI is gone
 * by the time the answer arrives, where a silent failure would look like success.
 *
 * Takes a setter so the announcement can live in its own flow or as a field of a larger state.
 * Lives here, not in `common/util`, because it names [FailedAction].
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
