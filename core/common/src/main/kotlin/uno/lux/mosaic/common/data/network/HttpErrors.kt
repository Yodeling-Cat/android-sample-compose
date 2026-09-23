package uno.lux.mosaic.common.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import uno.lux.mosaic.common.util.AppError
import java.net.ConnectException
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Runs [request], turning the server's 404 into `null`. "There is no such resource" is an answer,
 * not a failure: a caller has to tell it apart from a request that never got through, because only
 * one of the two is worth a retry button. Every other status still throws.
 */
suspend fun <T> notFoundAsNull(request: suspend () -> T): T? =
    try {
        request()
    } catch (e: HttpException) {
        if (e.code() == HTTP_NOT_FOUND) null else throw e
    }

/**
 * Maps a failure into the typed [AppError] a ViewModel stores and the UI renders. The one place a
 * thrown exception becomes an error the user sees, so every screen tells "no connection" apart
 * from "the server refused that".
 *
 * Lives here rather than beside [AppError] because naming [HttpException] is wire knowledge:
 * `common/util` imports nothing of the project's or the HTTP stack's, so the error type stays pure
 * while its interpretation sits with the wire.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is UnknownHostException, is ConnectException -> AppError.NoConnection
    is SocketTimeoutException -> AppError.Timeout
    is HttpException -> AppError.Http(code = code(), serverMessage = serverMessage())
    else -> AppError.Unknown
}

/**
 * The server's own description of what was wrong with the request, read from the standard Rails
 * error envelope `{"error": {"code", "message", "details"?}}`. Validation detail messages
 * ("Title is too long …") beat the envelope's summary ("Validation failed"), so the user sees the
 * rule that fired, not the fact that one did.
 *
 * Only client errors carry one: a 5xx body describes the server, not the request — in development
 * it is a raw exception message — so those fall through to the generic localized text. So does any
 * body this can't parse; a proxy's HTML error page must not end up in a snackbar.
 */
private fun HttpException.serverMessage(): String? {
    if (code() !in 400..499) return null
    val body = response()?.errorBody()?.string() ?: return null

    val envelope = try {
        errorJson.decodeFromString<ErrorEnvelopeDto>(body)
    } catch (_: SerializationException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }

    val details = envelope.error.details
        .map { it.message }
        .filter { it.isNotBlank() }
    val message = if (details.isNotEmpty()) details.joinToString(separator = "\n") else envelope.error.message

    return message.takeIf { it.isNotBlank() }
}

/** Lenient on purpose: an error body with fields this parser doesn't name must still yield its message. */
private val errorJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class ErrorEnvelopeDto(
    val error: ErrorBodyDto,
)

@Serializable
private data class ErrorBodyDto(
    val message: String,
    val details: List<ErrorDetailDto> = emptyList(),
)

@Serializable
private data class ErrorDetailDto(
    val message: String,
)
