package uno.lux.mosaic.common.data.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

val emptyPage = CursorPageDto(nextCursor = null, hasMore = false)

/**
 * The 404 Retrofit raises for an unknown id, error envelope and all — what a data source has to
 * turn back into a "there is no such thing" answer.
 */
fun notFoundException(message: String) = HttpException(
    Response.error<Any>(
        404,
        """{"error":{"code":"NOT_FOUND","message":"$message"}}"""
            .toResponseBody("application/json".toMediaType()),
    ),
)

/** The exception Retrofit raises for any non-2xx answer, with [body] as the server's error body. */
fun httpException(code: Int, body: String) = HttpException(
    Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
)
