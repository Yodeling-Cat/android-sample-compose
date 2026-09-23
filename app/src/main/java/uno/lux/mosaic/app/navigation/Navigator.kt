package uno.lux.mosaic.app.navigation

import java.util.UUID

/**
 * @param nextId must not be a counter: after process death a fresh instance would mint ids the
 *   restored stack still holds.
 */
class Navigator(
    private val nextId: () -> String = { UUID.randomUUID().toString() },
) {

    private var backStack: MutableList<BackStackEntry>? = null

    fun attach(backStack: MutableList<BackStackEntry>) {
        this.backStack = backStack
    }

    fun detach(backStack: MutableList<BackStackEntry>) {
        if (this.backStack === backStack) this.backStack = null
    }

    internal fun entryFor(screen: Screen) =
        BackStackEntry(screen, screen.sharedId ?: nextId())

    fun goTo(screen: Screen) {
        backStack?.add(entryFor(screen))
    }

    fun goToSingleTop(screen: Screen) {
        val backStack = backStack ?: return

        if (backStack.lastOrNull()?.screen != screen) backStack.add(entryFor(screen))
    }

    fun replaceTop(screen: Screen) {
        val backStack = backStack ?: return

        backStack.removeLastOrNull()
        backStack.add(entryFor(screen))
    }

    fun goBack() {
        backStack?.removeLastOrNull()
    }
}
