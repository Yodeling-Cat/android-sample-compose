package uno.lux.mosaic.shell.ui

import org.junit.Assert
import org.junit.Test
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens

class ShellViewModelTest {

    private val backStack = backStackOf(Screen.Shell)
    private val navigator = Navigator().apply { attach(backStack) }
    private val viewModel = ShellViewModel(navigator)

    @Test
    fun `an action destination pushes its screen over the shell`() {
        viewModel.openDestination(Screen.CreatePost)

        Assert.assertEquals(listOf(Screen.Shell, Screen.CreatePost), backStack.screens())
    }

    @Test
    fun `re-selecting an action destination does not stack a second copy`() {
        viewModel.openDestination(Screen.CreatePost)
        viewModel.openDestination(Screen.CreatePost)

        Assert.assertEquals(listOf(Screen.Shell, Screen.CreatePost), backStack.screens())
    }

    @Test
    fun `CREATE is the only destination that navigates`() {
        val navigating = ShellDestinations.entries.filter { it.screen != null }

        Assert.assertEquals(listOf(ShellDestinations.CREATE), navigating)
        Assert.assertEquals(Screen.CreatePost, ShellDestinations.CREATE.screen)
    }

    @Test
    fun `the tab destinations carry no screen, so selecting one never navigates`() {
        Assert.assertNull(ShellDestinations.HOME.screen)
        Assert.assertNull(ShellDestinations.PROFILE.screen)
    }
}
