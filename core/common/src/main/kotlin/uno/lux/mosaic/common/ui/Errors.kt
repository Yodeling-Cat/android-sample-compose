package uno.lux.mosaic.common.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import uno.lux.mosaic.common.data.network.toAppError
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.common.util.catchErrors

/**
 * Runs [block], writing any non-[CancellationException] into [errorSink] as an [AppError].
 * Convenience wrapper over [catchErrors] for the common ViewModel pattern of storing a typed
 * error alongside a reactive combine chain.
 *
 * Not in `common/util` with [catchErrors] because turning the exception into an
 * [AppError] needs [toAppError], which lives with the wire — and `common/util` imports nothing of
 * the project's.
 */
suspend fun ignoreErrors(errorSink: MutableStateFlow<AppError?>, block: suspend () -> Unit) =
    catchErrors(
        onError = { e -> errorSink.value = e.toAppError() },
        block = block,
    )
