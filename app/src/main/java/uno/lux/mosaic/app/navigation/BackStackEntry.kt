package uno.lux.mosaic.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import kotlinx.serialization.Serializable

/** [id] gives each push its own state: Navigation 3 scopes state by the key alone. */
@Serializable
data class BackStackEntry(
    val screen: Screen,
    val id: String,
) : NavKey

@Composable
fun rememberBackStack(navigator: Navigator, root: Screen): NavBackStack<BackStackEntry> =
    rememberSerializable(serializer = NavBackStackSerializer(BackStackEntry.serializer())) {
        NavBackStack(navigator.entryFor(root))
    }

/**
 * Not the `entryProvider { entry<T>() }` DSL: its metadata cache is never pruned, so with an
 * identity per push it would retain every entry ever pushed.
 */
fun backStackEntryProvider(
    content: @Composable (Screen) -> Unit,
): (BackStackEntry) -> NavEntry<BackStackEntry> = { entry ->
    NavEntry(key = entry, contentKey = entry.id) { current -> content(current.screen) }
}
