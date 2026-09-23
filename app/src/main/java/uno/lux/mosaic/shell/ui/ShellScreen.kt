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
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.designsystem.components.DividedNavigationSuiteScaffold
import uno.lux.mosaic.designsystem.theme.LocalMosaicColors
import uno.lux.mosaic.designsystem.theme.MosaicTheme
import uno.lux.mosaic.home.ui.HomeScreen
import uno.lux.mosaic.profile.ui.ProfileScreen
import uno.lux.mosaic.user.data.domain.UserId

/**
 * The tabbed shell: [DividedNavigationSuiteScaffold] adapts the navigation affordance to the
 * window size — bottom bar on phones, navigation rail on larger or unfolded screens — and the
 * selected [ShellDestinations] entry chooses which screen fills the content area. Tabs are peer
 * destinations held as plain selection state (switching tabs is not a navigation event and
 * never grows the back stack), so the content cross-fades (fade-through) rather than sliding.
 *
 * An [ShellDestinations] entry carrying a [Screen] breaks that rule deliberately: it is an action,
 * not a tab, and selecting it pushes that page over the whole shell through [ShellViewModel] —
 * covering the navigation bar, leaving the current tab selected underneath, and sliding in like
 * any other page. [ShellDestinations.CREATE] opens the composer this way.
 *
 * Back from any tab but [ShellDestinations.HOME] returns to Home first, and only back from Home
 * leaves the shell. That is the one way back touches the tabs: it never walks the tab history.
 */
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
                viewModel.onEvent(ShellUiEvent.OpenDestination(screen))
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

/**
 * The shell's frame with no ViewModel behind it: the navigation suite and the cross-fading content
 * area. [tabContent] draws the selected tab, which is what lets a preview stand a placeholder in for
 * the real screens and their ViewModels.
 *
 * [onSelectDestination] receives the destination the user asked for, by tapping its item or, off
 * Home, by pressing back.
 */
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

/**
 * One navigation item per [ShellDestinations] entry, in declaration order — which is what makes the
 * enum the single source of truth for the navigation suite: adding a destination adds an item, and
 * nothing here names one.
 *
 * [current] is the selected *tab*, so an action entry never reports as selected: it carries a
 * `screen`, [current] is only ever assigned a tab, and the two therefore never match — which is
 * what leaves the tab underneath highlighted while its page sits on top. What a click means is the
 * caller's to decide; [onClick] receives the entry itself.
 */
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

/** How long the outgoing tab takes to fade out, which is also how long the incoming one waits. */
private const val TAB_FADE_OUT_MILLIS = 90

/** How long the incoming tab's fade-and-scale runs, once the outgoing one has gone. */
private const val TAB_FADE_IN_MILLIS = 210

/** The incoming tab starts a touch small and settles into place, rather than snapping to size. */
private const val TAB_INITIAL_SCALE = 0.94f

/**
 * The tab switch: Material's **fade-through**, the transition between peers. The two halves don't
 * overlap — the outgoing tab fades out first and the incoming one waits that long before fading in,
 * scaling up the last sliver as it arrives. Nothing slides, because sliding would claim the tabs sit
 * in some spatial order; a page pushed *over* the shell slides instead (`pushTransition`).
 */
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
