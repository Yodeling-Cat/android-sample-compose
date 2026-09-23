package uno.lux.mosaic.common.util

import androidx.compose.runtime.Immutable

@Immutable
sealed interface AppError {
    data object NoConnection : AppError

    data object Timeout : AppError

    /** [serverMessage] is null for a 5xx, or when the body carried none. */
    data class Http(
        val code: Int,
        val serverMessage: String? = null,
    ) : AppError

    data object Unknown : AppError
}
