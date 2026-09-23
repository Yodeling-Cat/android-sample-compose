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

suspend fun <T> notFoundAsNull(request: suspend () -> T): T? =
    try {
        request()
    } catch (e: HttpException) {
        if (e.code() == HTTP_NOT_FOUND) null else throw e
    }

fun Throwable.toAppError(): AppError = when (this) {
    is UnknownHostException, is ConnectException -> AppError.NoConnection
    is SocketTimeoutException -> AppError.Timeout
    is HttpException -> AppError.Http(code = code(), serverMessage = serverMessage())
    else -> AppError.Unknown
}

/**
 * Only a 4xx describes the request. A 5xx body describes the server, and an unparseable one (a
 * proxy's HTML page) must not reach a snackbar.
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
