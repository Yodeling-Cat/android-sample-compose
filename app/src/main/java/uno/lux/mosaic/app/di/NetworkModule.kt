package uno.lux.mosaic.app.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.video.VideoFrameDecoder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uno.lux.mosaic.BuildConfig
import uno.lux.mosaic.comment.data.network.CommentApi
import uno.lux.mosaic.feed.data.network.FeedApi
import uno.lux.mosaic.post.data.network.PostApi
import uno.lux.mosaic.profile.data.network.ProfileApi
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.user.data.network.UserApi
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    const val BASE_URL: String = BuildConfig.BASE_URL
    private const val API_URL = "${BASE_URL}/api/"

    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    private const val HTTP_CACHE_BYTES = 10L * 1024 * 1024

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideHttpCache(
        @ApplicationContext context: Context,
    ): Cache =
        Cache(File(context.cacheDir, "http"), HTTP_CACHE_BYTES)

    /*
     * The timeouts are deliberate, not defaults: connect stays at 10 s, read and write get 30 s
     * because a Rails page under load can outlive the 10 s default. There is no call timeout on
     * purpose — a 25 MB video on a slow uplink is a legitimate multi-minute call, and the per-write
     * timeout still catches a stalled socket.
     *
     * There is no retry/backoff interceptor, also on purpose. OkHttp's own connection-level retry
     * stays on, but `POST /posts` and `POST …/comments` are not idempotent, so a blind re-send can
     * publish twice. Retry belongs to the user-visible retry paths instead.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        cache: Cache,
        @CurrentUserId currentUserId: UserId,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .cache(cache)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(UserIdHeaderInterceptor(currentUserId))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(BodyLoggingInterceptor)
                }
            }.build()

    /** Coil keeps its own disk cache, so the image client drops the HTTP one. */
    @Provides
    @Singleton
    @ImageHttpClient
    fun provideImageOkHttpClient(okHttpClient: OkHttpClient): OkHttpClient =
        okHttpClient.newBuilder().cache(null).build()

    /** The video-frame decoder renders the composer's thumbnail of a picked `content://` clip. */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @ImageHttpClient imageHttpClient: OkHttpClient,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = imageHttpClient))
                add(VideoFrameDecoder.Factory())
            }.build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(API_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()

    // One Retrofit instance, one service per slice. Retrofit will create any number of them,
    // and a service that names only its own slice's endpoints is what lets each data source
    // live beside the repository it serves rather than in a shared network package.

    @Provides
    @Singleton
    fun provideFeedApi(retrofit: Retrofit): FeedApi = retrofit.create(FeedApi::class.java)

    @Provides
    @Singleton
    fun providePostApi(retrofit: Retrofit): PostApi = retrofit.create(PostApi::class.java)

    @Provides
    @Singleton
    fun provideCommentApi(retrofit: Retrofit): CommentApi = retrofit.create(CommentApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImageHttpClient

private class UserIdHeaderInterceptor(
    private val userId: UserId,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain
            .request()
            .newBuilder()
            .addHeader("X-User-Id", userId)
            .build()

        return chain.proceed(request)
    }
}

/**
 * Logs multipart uploads at [HEADERS][HttpLoggingInterceptor.Level.HEADERS] only: their text
 * preamble passes the logger's UTF-8 check, so `BODY` would dump the whole video into logcat.
 */
private object BodyLoggingInterceptor : Interceptor {

    private val full = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    private val headersOnly =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }

    override fun intercept(chain: Interceptor.Chain): Response {
        val isMultipart = chain
            .request()
            .body
            ?.contentType()
            ?.type == "multipart"

        return (if (isMultipart) headersOnly else full).intercept(chain)
    }
}
