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
 * Navigation 3 scopes everything per-entry — the `rememberSaveable` state holder, the
 * `ViewModelStore`, the scene identity — by `NavEntry.contentKey`, which is a function of the
 * back-stack key *alone*: an entry provider is handed the key and nothing else, so nothing outside
 * the key can tell two identical entries apart. With a bare `Screen` as the key that made
 * identity a property of the *page*, and opening the same post twice handed the second page the
 * first one's ViewModel and its half-typed comment. Carrying [id] here moves identity onto the
 * position instead, where it belongs.
 *
 * [Navigator] is the only thing that mints an [id] — see [Navigator.entryFor] — so a new [Screen]
 * cannot forget to take part. Keeping identity out of [Screen] is also what preserves `Screen`
 * equality as a statement about *pages*, which is what [Navigator.goToSingleTop] asks about.
 *
 * The whole entry is `@Serializable`, so the stack survives process death exactly as before; the
 * polymorphism now sits on the sealed [Screen] field, resolved by the compiler rather than by
 * `NavKeySerializer`'s reflection.
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
