package uno.lux.mosaic.testing

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uno.lux.mosaic.app.di.NetworkModule

/**
 * Builds a real Retrofit service against this loopback server, using **production's own Json** —
 * not a copy of its settings, since a test that pins the wire format against a config nobody
 * ships would keep passing while the real one drifted.
 */
fun <T> MockWebServer.createApi(service: Class<T>): T =
    Retrofit
        .Builder()
        .baseUrl(url("/api/"))
        .addConverterFactory(
            NetworkModule
                .provideJson()
                .asConverterFactory("application/json; charset=UTF-8".toMediaType()),
        ).build()
        .create(service)
