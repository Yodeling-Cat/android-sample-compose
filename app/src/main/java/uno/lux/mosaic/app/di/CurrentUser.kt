package uno.lux.mosaic.app.di

import javax.inject.Qualifier

/** Qualifies the injected [uno.lux.mosaic.user.data.domain.User] object for the signed-in user. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CurrentUser
