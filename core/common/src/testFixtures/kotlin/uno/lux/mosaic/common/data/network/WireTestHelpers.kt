package uno.lux.mosaic.common.data.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

val emptyPage = CursorPageDto(nextCursor = null, hasMore = false)

fun notFoundException(message: String) = HttpException(
    Response.error<Any>(
        404,
        """{"error":{"code":"NOT_FOUND","message":"$message"}}"""
            .toResponseBody("application/json".toMediaType()),
    ),
)

fun httpException(code: Int, body: String) = HttpException(
    Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())),
)
