package uno.lux.mosaic.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import uno.lux.mosaic.app.navigation.Navigator

@Module
@InstallIn(ActivityRetainedComponent::class)
object NavigationModule {

    @Provides
    @ActivityRetainedScoped
    fun provideNavigator(): Navigator =
        Navigator()
}
