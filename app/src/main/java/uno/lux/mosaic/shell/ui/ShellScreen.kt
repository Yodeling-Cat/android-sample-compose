package uno.lux.mosaic.shell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import uno.lux.mosaic.designsystem.components.DividedNavigationSuiteScaffold
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.home.ui.HomeScreen
import uno.lux.mosaic.profile.ui.ProfileScreen
import uno.lux.mosaic.user.data.domain.UserId
import uno.lux.mosaic.shell.ui.ShellUiEvent as UiEvent

@Composable
fun ShellScreen(
    currentUserId: UserId,
    viewModel: ShellViewModel = hiltViewModel(),
) {
    var currentDestination by rememberSaveable { mutableStateOf(ShellDestinations.HOME) }

    ShellScreen(
        currentDestination = currentDestination,
        onSelectDestination = { destination ->
            // An entry carrying a screen is an action, not a tab: it pushes that page over the
            // whole shell and leaves the current tab selected underneath.
            val screen = destination.screen

            if (screen != null) {
                viewModel.onEvent(UiEvent.OpenDestination(screen))
            } else {
                currentDestination = destination
            }
        },
    ) { destination ->
        when (destination) {
            ShellDestinations.HOME -> HomeScreen()

            ShellDestinations.PROFILE -> ProfileScreen(userId = currentUserId)

            // CREATE pushes Screen.CreatePost over the shell rather than filling the
            // content area, so it is never the selected destination and never renders here.
            ShellDestinations.CREATE -> Unit
        }
    }
}

@Composable
internal fun ShellScreen(
    currentDestination: ShellDestinations,
    onSelectDestination: (ShellDestinations) -> Unit,
    tabContent: @Composable (ShellDestinations) -> Unit,
) {
    BackHandler(enabled = currentDestination != ShellDestinations.HOME) {
        onSelectDestination(ShellDestinations.HOME)
    }

    val navItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = LocalMosaicColors.current.textTertiary,
        ),
    )

    DividedNavigationSuiteScaffold(
        navigationSuiteItems = {
            destinationItems(
                current = currentDestination,
                colors = navItemColors,
                onClick = onSelectDestination,
            )
        },
    ) {
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = { tabTransition() },
            modifier = Modifier.fillMaxSize(),
            label = "tab",
        ) { destination ->
            tabContent(destination)
        }
    }
}

private fun NavigationSuiteScope.destinationItems(
    current: ShellDestinations,
    colors: NavigationSuiteItemColors,
    onClick: (ShellDestinations) -> Unit,
) = ShellDestinations.entries.forEach { destination ->
    item(
        icon = {
            Icon(
                painterResource(destination.icon),
                contentDescription = stringResource(destination.labelRes),
            )
        },
        label = { Text(stringResource(destination.labelRes)) },
        selected = destination == current,
        onClick = { onClick(destination) },
        colors = colors,
    )
}

private const val TAB_FADE_OUT_MILLIS = 90

private const val TAB_FADE_IN_MILLIS = 210

private const val TAB_INITIAL_SCALE = 0.94f

private fun tabTransition(): ContentTransform {
    val enter = tween<Float>(durationMillis = TAB_FADE_IN_MILLIS, delayMillis = TAB_FADE_OUT_MILLIS)

    return (fadeIn(enter) + scaleIn(enter, initialScale = TAB_INITIAL_SCALE)) togetherWith
        fadeOut(tween(durationMillis = TAB_FADE_OUT_MILLIS))
}

@Preview(name = "Phone", showBackground = true)
@Preview(name = "Tablet", showBackground = true, device = Devices.PIXEL_TABLET)
@Composable
private fun ShellScreenPreview() {
    MosaicTheme {
        ShellScreen(
            currentDestination = ShellDestinations.HOME,
            onSelectDestination = {},
        ) { destination ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(destination.labelRes))
            }
        }
    }
}
