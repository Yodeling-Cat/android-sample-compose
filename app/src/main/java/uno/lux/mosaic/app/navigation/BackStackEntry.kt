package uno.lux.mosaic.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import kotlinx.serialization.Serializable

/**
 * One position on the back stack: the [screen] to render, plus the [id] that gives that position
 * its own state.
 *
 * Navigation 3 scopes saveable state and the `ViewModelStore` by a function of the key alone, so
 * with a bare [Screen] as the key, two pushes of the same page would share one ViewModel. [id]
 * makes each push its own page. Only [Navigator.entryFor] mints one, and keeping it out of
 * [Screen] leaves `Screen` equality meaning "same page", which [Navigator.goToSingleTop] relies on.
 */
@Serializable
data class BackStackEntry(
    val screen: Screen,
    val id: String,
) : NavKey

/**
 * The composition-owned back stack, seeded with [root] on first creation and restored from the
 * instance state after that.
 *
 * Stands in for `rememberNavBackStack`, which returns an untyped `NavBackStack<NavKey>`, so the
 * stack stays a list of [BackStackEntry] end to end.
 */
@Composable
fun rememberBackStack(navigator: Navigator, root: Screen): NavBackStack<BackStackEntry> =
    rememberSerializable(serializer = NavBackStackSerializer(BackStackEntry.serializer())) {
        NavBackStack(navigator.entryFor(root))
    }

/**
 * The `NavDisplay` entry provider: renders [content] for each entry's screen, keyed by
 * [BackStackEntry.id] so state belongs to the position, not to the page.
 *
 * Not the `entryProvider { entry<T>() }` DSL: its metadata cache is keyed by the whole key and
 * never pruned, so with an identity per push it would retain every entry ever pushed.
 */
fun backStackEntryProvider(
    content: @Composable (Screen) -> Unit,
): (BackStackEntry) -> NavEntry<BackStackEntry> = { entry ->
    NavEntry(key = entry, contentKey = entry.id) { current -> content(current.screen) }
}
