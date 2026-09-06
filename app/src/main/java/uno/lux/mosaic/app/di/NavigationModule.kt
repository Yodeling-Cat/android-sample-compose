package uno.lux.mosaic.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import uno.lux.mosaic.app.navigation.Navigator

/**
 * Provides the [Navigator] ViewModels navigate through. Retained-activity scope makes it a
 * single instance shared by the activity's UI (which attaches the back stack) and every
 * ViewModel, surviving configuration changes together with the ViewModels that hold it.
 */
@Module
@InstallIn(ActivityRetainedComponent::class)
object NavigationModule {

    @Provides
    @ActivityRetainedScoped
    fun provideNavigator(): Navigator = Navigator()
}
