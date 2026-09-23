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
 * The composition-owned back stack, seeded with [root] the first time it is created and restored
 * from the instance state on every recreation after that — the guarantee `MosaicApp` rests on.
 * [navigator] mints the root's identity, so that stays in the one place that does it.
 *
 * This stands in for `rememberNavBackStack`, whose overloads both return a `NavBackStack<NavKey>`
 * serialized through a reflective per-element `NavKeySerializer`. [BackStackEntry] is the only
 * key type this app has, so naming it here keeps the stack typed end to end — [Navigator] takes a
 * `MutableList<BackStackEntry>` and needs no cast to read the screen off the top entry.
 */
@Composable
fun rememberBackStack(navigator: Navigator, root: Screen): NavBackStack<BackStackEntry> =
    rememberSerializable(serializer = NavBackStackSerializer(BackStackEntry.serializer())) {
        NavBackStack(navigator.entryFor(root))
    }

/**
 * The `NavDisplay` entry provider: renders [content] for whichever screen an entry holds, keyed by
 * [BackStackEntry.id]. That key is the whole guarantee — "state belongs to the position, not to
 * the page" — so it is stated here once rather than by each host that builds a display.
 *
 * Deliberately not the `entryProvider { entry<T>() }` DSL. That builds a `KClass`-keyed map to
 * dispatch between registered key types, which buys nothing when there is exactly one, and its
 * metadata cache is keyed by the whole key and never pruned — so with an identity per push it
 * would retain every entry ever pushed, and their `Screen` payloads (an album's image URLs) with
 * them.
 */
fun backStackEntryProvider(
    content: @Composable (Screen) -> Unit,
): (BackStackEntry) -> NavEntry<BackStackEntry> = { entry ->
    NavEntry(key = entry, contentKey = entry.id) { current -> content(current.screen) }
}
