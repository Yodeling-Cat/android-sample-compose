package uno.lux.mosaic.common.util

/**
 * Typed error result for any operation that can fail. Used by ViewModels to carry structured
 * failure information instead of a bare boolean, so the UI layer can map each case to a
 * localized message via [uno.lux.mosaic.common.asText].
 *
 * The mapping from thrown exceptions lives with the wire, in
 * [uno.lux.mosaic.common.data.network.toAppError], because recognising the HTTP client's
 * exception type is wire knowledge — this file stays free of project and library imports.
 */
sealed interface AppError {
    data object NoConnection : AppError

    data object Timeout : AppError

    /**
     * The server answered the request with an error status. [serverMessage] is the human-readable
     * message parsed out of the error body of a client error (4xx) — the server's own description
     * of what was wrong with the request, which matters the day a client-side mirror of a server
     * validation drifts and the server's structured 422 is all the user has to go on. It is `null`
     * when the body carried none, and for 5xx, whose message describes the server rather than the
     * request; the UI then falls back to a generic localized message naming [code].
     */
    data class Http(
        val code: Int,
        val serverMessage: String? = null,
    ) : AppError

    data object Unknown : AppError
}
