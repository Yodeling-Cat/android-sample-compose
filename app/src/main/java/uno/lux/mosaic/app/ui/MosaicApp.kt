package uno.lux.mosaic.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import uno.lux.mosaic.album.ui.AlbumViewerScreen
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.app.navigation.backStackEntryProvider
import uno.lux.mosaic.app.navigation.popTransition
import uno.lux.mosaic.app.navigation.pushTransition
import uno.lux.mosaic.app.navigation.rememberBackStack
import uno.lux.mosaic.composer.ui.CreatePostScreen
import uno.lux.mosaic.post.ui.PostDetailScreen
import uno.lux.mosaic.profile.ui.ProfileScreen
import uno.lux.mosaic.settings.ui.SettingsScreen
import uno.lux.mosaic.shell.ui.ShellScreen
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.user.ui.EditProfileScreen
import uno.lux.mosaic.video.ui.FullscreenVideoScreen
import uno.lux.mosaic.video.ui.ProvideVideoPlayback

/**
 * The app's root: renders the [Screen] on top of the back stack, with [Screen.Shell] as the
 * permanent root.
 *
 * ViewModels navigate through [navigator], so this wires no navigation lambdas. It only maps each
 * [Screen] to its page.
 */
@Composable
fun MosaicApp(currentUserId: UserId, navigator: Navigator) {
    val backStack = rememberBackStack(navigator, root = Screen.Shell)

    DisposableEffect(navigator, backStack) {
        navigator.attach(backStack)
        onDispose { navigator.detach(backStack) }
    }

    ProvideVideoPlayback {
        NavDisplay(
            backStack = backStack,
            onBack = navigator::goBack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { pushTransition() },
            popTransitionSpec = { popTransition() },
            predictivePopTransitionSpec = { popTransition() },
            modifier = Modifier.fillMaxSize(),
            entryProvider = backStackEntryProvider { screen ->
                ScreenContent(screen = screen, currentUserId = currentUserId)
            },
        )
    }
}

@Composable
private fun ScreenContent(screen: Screen, currentUserId: UserId) {
    when (screen) {
        Screen.Shell -> {
            ShellScreen(currentUserId = currentUserId)
        }

        is Screen.Profile -> {
            ProfileScreen(userId = screen.userId, showBackButton = true)
        }

        Screen.Settings -> {
            SettingsScreen()
        }

        Screen.EditProfile -> {
            EditProfileScreen()
        }

        Screen.CreatePost -> {
            CreatePostScreen()
        }

        is Screen.FullscreenVideo -> {
            FullscreenVideoScreen(url = screen.url, title = screen.title)
        }

        is Screen.PostDetail -> {
            PostDetailScreen(postId = screen.postId)
        }

        is Screen.AlbumViewer -> {
            AlbumViewerScreen(
                imageUrls = screen.images,
                initialIndex = screen.initialIndex,
            )
        }
    }
}
