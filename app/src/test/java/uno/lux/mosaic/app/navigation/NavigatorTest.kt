package uno.lux.mosaic.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens

class NavigatorTest {

    private val navigator = Navigator()
    private val backStack = backStackOf(Screen.Shell)

    @Test
    fun `goTo pushes onto the attached back stack`() {
        navigator.attach(backStack)

        navigator.goTo(Screen.Profile("u1"))
        navigator.goTo(Screen.Settings)

        assertEquals(
            listOf(Screen.Shell, Screen.Profile("u1"), Screen.Settings),
            backStack.screens(),
        )
    }

    @Test
    fun `goTo allows pushing a screen equal to the current top`() {
        navigator.attach(backStack)

        navigator.goTo(Screen.Profile("u1"))
        navigator.goTo(Screen.Profile("u1"))

        assertEquals(
            listOf(Screen.Shell, Screen.Profile("u1"), Screen.Profile("u1")),
            backStack.screens(),
        )
    }

    @Test
    fun `goToSingleTop pushes when the screen is not on top`() {
        navigator.attach(backStack)

        navigator.goToSingleTop(Screen.Settings)

        assertEquals(listOf(Screen.Shell, Screen.Settings), backStack.screens())
    }

    @Test
    fun `goToSingleTop is a no-op when the screen already sits on top`() {
        navigator.attach(backStack)
        navigator.goTo(Screen.Settings)

        navigator.goToSingleTop(Screen.Settings)

        assertEquals(listOf(Screen.Shell, Screen.Settings), backStack.screens())
    }

    @Test
    fun `goToSingleTop pushes when the same screen is buried but not on top`() {
        navigator.attach(backStack)
        navigator.goTo(Screen.Settings)
        navigator.goTo(Screen.Profile("u1"))

        navigator.goToSingleTop(Screen.Settings)

        assertEquals(
            listOf(Screen.Shell, Screen.Settings, Screen.Profile("u1"), Screen.Settings),
            backStack.screens(),
        )
    }

    @Test
    fun `goBack pops the top entry`() {
        navigator.attach(backStack)
        navigator.goTo(Screen.Settings)

        navigator.goBack()

        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `replaceTop swaps the top entry, leaving what sat below it`() {
        navigator.attach(backStack)
        navigator.goTo(Screen.CreatePost)

        navigator.replaceTop(Screen.PostDetail("p1"))

        assertEquals(listOf(Screen.Shell, Screen.PostDetail("p1")), backStack.screens())
    }

    @Test
    fun `replaceTop leaves entries below the top untouched`() {
        navigator.attach(backStack)
        navigator.goTo(Screen.Profile("u1"))
        navigator.goTo(Screen.CreatePost)

        navigator.replaceTop(Screen.PostDetail("p1"))

        assertEquals(
            listOf(Screen.Shell, Screen.Profile("u1"), Screen.PostDetail("p1")),
            backStack.screens(),
        )
    }

    @Test
    fun `navigation is dropped while no back stack is attached`() {
        navigator.goTo(Screen.Settings)
        navigator.replaceTop(Screen.Settings)
        navigator.goBack()

        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `navigation is dropped after the attached stack detaches`() {
        navigator.attach(backStack)
        navigator.detach(backStack)

        navigator.goTo(Screen.Settings)

        assertEquals(listOf(Screen.Shell), backStack.screens())
    }

    @Test
    fun `detaching a stale stack keeps the current one attached`() {
        val staleStack = backStackOf(Screen.Shell)
        navigator.attach(staleStack)
        navigator.attach(backStack)

        navigator.detach(staleStack)
        navigator.goTo(Screen.Settings)

        assertEquals(listOf(Screen.Shell, Screen.Settings), backStack.screens())
    }

    //
    // Every per-entry store Navigation 3 keeps — the ViewModel store, the rememberSaveable
    // holder — is scoped by the entry's id, so these are what decide whether two open copies of
    // a page share their state.

    @Test
    fun `each push of the same screen gets its own identity`() {
        navigator.attach(backStack)

        navigator.goTo(Screen.PostDetail("p1"))
        navigator.goTo(Screen.Profile("u1"))
        navigator.goTo(Screen.PostDetail("p1"))

        val (first, second) = backStack.filter { it.screen == Screen.PostDetail("p1") }
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `replaceTop gives the replacement its own identity`() {
        navigator.attach(backStack)
        navigator.goTo(Screen.PostDetail("p1"))
        val replaced = backStack.last()

        navigator.replaceTop(Screen.PostDetail("p1"))

        assertNotEquals(replaced.id, backStack.last().id)
    }

    @Test
    fun `a screen pinning a shared id keeps one identity across pushes`() {
        navigator.attach(backStack)

        navigator.goTo(Screen.Shell)

        // Screen.Shell pins Screen.sharedId, so the pushed entry resolves to the identity the
        // root already holds — the opt-in that gives a page one ViewModel and one set of state
        // wherever it is opened, which is what every other screen deliberately gives up.
        assertEquals(backStack[0].id, backStack[1].id)
    }

    @Test
    fun `a screen without a shared id takes the generated one`() {
        val navigator = Navigator(nextId = { "generated" })
        navigator.attach(backStack)

        navigator.goTo(Screen.Settings)

        assertEquals("generated", backStack.last().id)
    }
}
