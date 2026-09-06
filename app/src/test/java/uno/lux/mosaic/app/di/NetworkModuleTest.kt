package uno.lux.mosaic.app.di

import okhttp3.Cache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

/**
 * Pins the deliberate parts of the HTTP stack: the explicit timeouts, the cache on the API
 * client, and the image client sharing the API client's pool while carrying no cache of its own.
 */
class NetworkModuleTest {

    private val cache = Cache(createTempDirectory("http").toFile(), maxSize = 1024)
    private val client = NetworkModule.provideOkHttpClient(cache, currentUserId = "u1")

    @Test
    fun `the api client uses the provided cache`() {
        assertSame(cache, client.cache)
    }

    @Test
    fun `timeouts are the deliberate values, with no call timeout`() {
        assertEquals(NetworkModule.CONNECT_TIMEOUT_SECONDS * 1000, client.connectTimeoutMillis.toLong())
        assertEquals(NetworkModule.READ_TIMEOUT_SECONDS * 1000, client.readTimeoutMillis.toLong())
        assertEquals(NetworkModule.WRITE_TIMEOUT_SECONDS * 1000, client.writeTimeoutMillis.toLong())
        assertEquals(0, client.callTimeoutMillis)
    }

    @Test
    fun `connection-level retry stays on`() {
        assertTrue(client.retryOnConnectionFailure)
    }

    @Test
    fun `the image client shares the pool and interceptors but carries no cache`() {
        val imageClient = NetworkModule.provideImageOkHttpClient(client)

        assertSame(client.connectionPool, imageClient.connectionPool)
        assertSame(client.dispatcher, imageClient.dispatcher)
        assertEquals(client.interceptors, imageClient.interceptors)
        assertNull(imageClient.cache)
    }
}
