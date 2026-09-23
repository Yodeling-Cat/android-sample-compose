package uno.lux.mosaic.app.navigation

import java.util.UUID

/**
 * Lets ViewModels navigate by intent ([goTo], [goBack], [replaceTop]) instead of screens taking
 * navigation lambdas from the host.
 *
 * The composition owns the back stack, so it survives process death, and [attach]es it here.
 * Calls made while no stack is attached are dropped.
 *
 * @param nextId mints each entry's identity. It must not be a counter: this class is
 *   `@ActivityRetainedScoped`, so after process death a fresh instance would restart the count
 *   while the restored stack still holds the old ids.
 */
class Navigator(
    private val nextId: () -> String = { UUID.randomUUID().toString() },
) {

    private var backStack: MutableList<BackStackEntry>? = null

    /** Binds [backStack] as the stack [goTo] and [goBack] mutate. */
    fun attach(backStack: MutableList<BackStackEntry>) {
        this.backStack = backStack
    }

    /** Releases [backStack] if it is still the attached stack; a newer attachment stays. */
    fun detach(backStack: MutableList<BackStackEntry>) {
        if (this.backStack === backStack) this.backStack = null
    }

    /**
     * Builds the entry for [screen] with a fresh identity, unless the screen pins one through
     * [Screen.sharedId].
     *
     * `internal` rather than private because [rememberBackStack] and the tests build entries with it.
     */
    internal fun entryFor(screen: Screen) =
        BackStackEntry(screen, screen.sharedId ?: nextId())

    /**
     * Pushes [screen], even when an equal screen is already on top, so a deliberate re-open works.
     * Pages that must never stack on themselves use [goToSingleTop].
     */
    fun goTo(screen: Screen) {
        backStack?.add(entryFor(screen))
    }

    /**
     * Pushes [screen] unless an equal screen is already on top. For pages that are one of a kind
     * wherever they open, such as Settings. Unlike the click debounce, this holds whatever the tap
     * timing.
     */
    fun goToSingleTop(screen: Screen) {
        val backStack = backStack ?: return

        if (backStack.lastOrNull()?.screen != screen) backStack.add(entryFor(screen))
    }

    /**
     * Swaps the top entry for [screen], so back from [screen] skips the replaced page. The composer
     * hands off to the published post this way.
     */
    fun replaceTop(screen: Screen) {
        val backStack = backStack ?: return

        backStack.removeLastOrNull()
        backStack.add(entryFor(screen))
    }

    /** Pops the top entry off the back stack. */
    fun goBack() {
        backStack?.removeLastOrNull()
    }
}
