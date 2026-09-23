package uno.lux.mosaic.common.data.network

import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.common.util.AppError
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ToAppErrorTest {

    @Test
    fun `connectivity failures map to their own cases`() {
        assertEquals(AppError.NoConnection, UnknownHostException("host").toAppError())
        assertEquals(AppError.NoConnection, ConnectException("refused").toAppError())
        assertEquals(AppError.Timeout, SocketTimeoutException("slow").toAppError())
    }

    @Test
    fun `anything unrecognised maps to Unknown`() {
        assertEquals(AppError.Unknown, IllegalStateException("bug").toAppError())
    }

    @Test
    fun `a validation refusal carries the server's detail messages`() {
        val exception = httpException(
            422,
            """
            {"error":{"code":"VALIDATION_ERROR","message":"Validation failed","details":[
              {"path":"title","code":"too_long","message":"Title is too long (maximum is 120 characters)"},
              {"path":"body","code":"blank","message":"Body can't be blank"}
            ]}}
            """.trimIndent(),
        )

        assertEquals(
            AppError.Http(
                code = 422,
                serverMessage = "Title is too long (maximum is 120 characters)\nBody can't be blank",
            ),
            exception.toAppError(),
        )
    }

    @Test
    fun `a refusal without details falls back to the envelope message`() {
        val exception = httpException(403, """{"error":{"code":"FORBIDDEN","message":"You do not own this post"}}""")

        assertEquals(AppError.Http(code = 403, serverMessage = "You do not own this post"), exception.toAppError())
    }

    @Test
    fun `a server-side failure keeps only its status code`() {
        val exception = httpException(500, """{"error":{"code":"INTERNAL","message":"undefined method for nil"}}""")

        assertEquals(AppError.Http(code = 500, serverMessage = null), exception.toAppError())
    }

    @Test
    fun `an unparseable error body maps to the bare status`() {
        val exception = httpException(422, "<html>Bad Gateway</html>")

        assertEquals(AppError.Http(code = 422, serverMessage = null), exception.toAppError())
    }
}
