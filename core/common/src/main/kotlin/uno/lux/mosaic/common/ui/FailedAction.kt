package uno.lux.mosaic.common.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import uno.lux.mosaic.common.asText
import uno.lux.mosaic.common.util.catchErrors

// TODO: Using a global enum for this is terrible architecture. Should probably take any data class as argument instead.

enum class FailedAction {
    DELETE_POST,
    FOLLOW,
    SEND_COMMENT,
}

fun ViewModel.launchReporting(
    action: FailedAction,
    onFailed: (FailedAction) -> Unit,
    block: suspend () -> Unit,
) {
    viewModelScope.launch {
        catchErrors(onError = { onFailed(action) }, block = block)
    }
}

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
