package uno.lux.mosaic.album.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens

class AlbumViewerViewModelTest {

    private val backStack = backStackOf(
        Screen.Shell,
        Screen.AlbumViewer(listOf("https://example.test/1.jpg"), initialIndex = 0),
    )
    private val navigator = Navigator().apply { attach(backStack) }

    @Test
    fun `GoBack pops the viewer`() {
        AlbumViewerViewModel(navigator).onEvent(AlbumViewerUiEvent.GoBack)

        assertEquals(listOf(Screen.Shell), backStack.screens())
    }
}
