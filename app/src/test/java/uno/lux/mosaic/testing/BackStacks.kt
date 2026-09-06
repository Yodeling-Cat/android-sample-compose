package uno.lux.mosaic.testing

import uno.lux.mosaic.app.navigation.BackStackEntry
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen

/*
 * Building and reading back stacks in tests. The app's stack holds BackStackEntry — a screen plus
 * the identity its state is scoped to — but a test that is about *navigation* cares which pages
 * are on the stack, not which copy of them, so these two keep that noise out of the assertions.
 */

/**
 * A back stack holding [screens], built exactly the way [Navigator] builds one so the identity
 * rule lives in a single place. Ids come from a running count rather than a UUID only so that a
 * failure message names the position that differed.
 */
fun backStackOf(vararg screens: Screen): MutableList<BackStackEntry> {
    val ids = generateSequence(1) { it + 1 }.map { "entry-$it" }.iterator()
    val navigator = Navigator(nextId = ids::next)

    return screens.mapTo(mutableListOf(), navigator::entryFor)
}

/** The pages on a back stack, for assertions about where navigation went rather than about state. */
fun List<BackStackEntry>.screens(): List<Screen> = map { it.screen }
