package uno.lux.mosaic.common.ui

import kotlinx.coroutines.flow.MutableStateFlow
import uno.lux.mosaic.common.data.network.toAppError
import uno.lux.mosaic.common.util.AppError
import uno.lux.mosaic.common.util.catchErrors

suspend fun ignoreErrors(errorSink: MutableStateFlow<AppError?>, block: suspend () -> Unit) =
    catchErrors(
        onError = { e -> errorSink.value = e.toAppError() },
        block = block,
    )
