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
     * Builds the entry [screen] occupies once pushed, giving it a fresh identity — and with it its
     * own ViewModel and `rememberSaveable` state — unless the screen pins one through
     * [Screen.sharedId], in which case every push resolves to the same position's state.
     *
     * Visible to the module rather than private because [rememberBackStack] seeds the stack's root
     * entry with it, and tests build a stack the way the app builds one; it is not part of the
     * surface ViewModels navigate through.
     */
    internal fun entryFor(screen: Screen) =
        BackStackEntry(screen, screen.sharedId ?: nextId())

    /**
     * Pushes [screen] on top of the back stack. Deliberately allows a screen equal to the current
     * top (e.g. the same profile opened from a post on that profile); the click debounce every
     * navigation control carries only guards against an accidental fast double-tap, not this
     * intentional re-open. Screens that must never stack on themselves use [goToSingleTop].
     */
    fun goTo(screen: Screen) {
        backStack?.add(entryFor(screen))
    }

    /**
     * Pushes [screen] unless it already sits on top of the back stack — the "single top" launch
     * behaviour. Used for pages that are semantically unique wherever they're reached (Settings,
     * the profile editor): re-invoking the affordance while the page is showing is a no-op rather
     * than a second copy on the stack. This is a real guarantee independent of tap timing, which
     * is why it can't be left to the debounce.
     *
     * The comparison is against the top entry's *screen*, which is the question being asked —
     * "is this page already showing?" — and is exactly what identity living on the entry rather
     * than inside the key keeps answerable.
     */
    fun goToSingleTop(screen: Screen) {
        val backStack = backStack ?: return

        if (backStack.lastOrNull()?.screen != screen) backStack.add(entryFor(screen))
    }

    /**
     * Swaps the top entry for [screen] — pop and push as one step. Used by a page that has served
     * its purpose and hands off to another: back from [screen] then returns to whatever sat below
     * the replaced page, not to the page itself. The composer replaces itself with the published
     * post's detail page this way, so backing out of that post lands on the feed rather than on a
     * composer the user is done with.
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
