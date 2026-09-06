package uno.lux.mosaic.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uno.lux.mosaic.app.fixtures.LOGGED_IN_USER_ID
import uno.lux.mosaic.app.fixtures.SampleUsers
import uno.lux.mosaic.comment.data.CommentDataSource
import uno.lux.mosaic.comment.data.CommentRepository
import uno.lux.mosaic.comment.data.network.CommentApi
import uno.lux.mosaic.comment.data.network.NetworkCommentDataSource
import uno.lux.mosaic.common.data.files.AndroidFileLoader
import uno.lux.mosaic.common.data.files.AndroidVideoMetadataReader
import uno.lux.mosaic.common.data.files.FileLoader
import uno.lux.mosaic.common.data.files.VideoMetadataReader
import uno.lux.mosaic.feed.data.FeedDataSource
import uno.lux.mosaic.feed.data.FeedRepository
import uno.lux.mosaic.feed.data.network.FeedApi
import uno.lux.mosaic.feed.data.network.NetworkFeedDataSource
import uno.lux.mosaic.post.data.PostDataSource
import uno.lux.mosaic.post.data.PostRepository
import uno.lux.mosaic.post.data.network.NetworkPostDataSource
import uno.lux.mosaic.post.data.network.PostApi
import uno.lux.mosaic.profile.data.ProfileDataSource
import uno.lux.mosaic.profile.data.ProfileRepository
import uno.lux.mosaic.profile.data.network.NetworkProfileDataSource
import uno.lux.mosaic.profile.data.network.ProfileApi
import uno.lux.mosaic.settings.data.AppCompatLocaleRepository
import uno.lux.mosaic.settings.data.AppLocaleRepository
import uno.lux.mosaic.settings.data.DataStoreSettingsRepository
import uno.lux.mosaic.settings.data.SettingsRepository
import uno.lux.mosaic.user.data.UserDataSource
import uno.lux.mosaic.user.data.UserRepository
import uno.lux.mosaic.user.data.domain.User
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.user.data.network.NetworkUserDataSource
import uno.lux.mosaic.user.data.network.UserApi
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    fun providePostDataSource(api: PostApi): PostDataSource = NetworkPostDataSource(api)

    @Provides
    @Singleton
    fun providePostRepository(
        dataSource: PostDataSource,
        userRepository: UserRepository,
    ): PostRepository = PostRepository(dataSource, userRepository)

    @Provides
    fun provideFeedDataSource(api: FeedApi): FeedDataSource = NetworkFeedDataSource(api)

    @Provides
    fun provideUserDataSource(api: UserApi): UserDataSource = NetworkUserDataSource(api)

    @Provides
    @Singleton
    fun provideUserRepository(dataSource: UserDataSource): UserRepository =
        UserRepository(dataSource)

    @Provides
    @Singleton
    fun provideFeedRepository(
        dataSource: FeedDataSource,
        postRepository: PostRepository,
        userRepository: UserRepository,
    ): FeedRepository = FeedRepository(dataSource, postRepository, userRepository)

    @Provides
    fun provideProfileDataSource(api: ProfileApi): ProfileDataSource = NetworkProfileDataSource(api)

    @Provides
    @Singleton
    fun provideProfileRepository(
        dataSource: ProfileDataSource,
        postRepository: PostRepository,
        userRepository: UserRepository,
        @CurrentUserId currentUserId: UserId,
    ): ProfileRepository =
        ProfileRepository(dataSource, postRepository, userRepository, currentUserId)

    @Provides
    fun provideCommentDataSource(api: CommentApi): CommentDataSource = NetworkCommentDataSource(api)

    @Provides
    @Singleton
    fun provideCommentRepository(dataSource: CommentDataSource): CommentRepository =
        CommentRepository(dataSource)

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideAppLocaleRepository(settingsRepository: SettingsRepository): AppLocaleRepository =
        AppCompatLocaleRepository(settingsRepository)

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    fun provideFileLoader(
        @ApplicationContext context: Context,
    ): FileLoader =
        AndroidFileLoader(context)

    @Provides
    fun provideVideoMetadataReader(
        @ApplicationContext context: Context,
    ): VideoMetadataReader =
        AndroidVideoMetadataReader(context)

    @Provides
    @CurrentUserId
    fun provideCurrentUserId(): String = LOGGED_IN_USER_ID

    @Provides
    @Singleton
    @CurrentUser
    fun provideCurrentUser(
        @CurrentUserId currentUserId: UserId,
    ): User =
        SampleUsers.first { it.id == currentUserId }
}
