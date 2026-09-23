package uno.lux.mosaic.video.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import uno.lux.mosaic.testing.backStackOf
import uno.lux.mosaic.testing.screens

class FullscreenVideoViewModelTest {

    private val backStack = backStackOf(
        Screen.Shell,
        Screen.FullscreenVideo(url = "https://example.test/v1.mp4", title = "Talk"),
    )
    private val navigator = Navigator().apply { attach(backStack) }

    @Test
    fun `GoBack pops the video page`() {
        FullscreenVideoViewModel(navigator).onEvent(FullscreenVideoUiEvent.GoBack)

        assertEquals(listOf(Screen.Shell), backStack.screens())
    }
}
