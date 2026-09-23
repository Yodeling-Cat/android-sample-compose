package uno.lux.mosaic.testing

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uno.lux.mosaic.app.di.NetworkModule

/** Uses production's own Json, so the wire format is pinned against the config that ships. */
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
