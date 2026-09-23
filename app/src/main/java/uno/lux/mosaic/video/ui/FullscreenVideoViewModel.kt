package uno.lux.mosaic.video.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import uno.lux.mosaic.app.navigation.Navigator
import javax.inject.Inject

/**
 * The full-screen video page reuses the shared player from `LocalVideoPlayback` and takes its
 * video from its back-stack key, so it owns no screen state; its ViewModel exists only to route
 * the back affordance (the app-bar button and the player's own exit-fullscreen control) through
 * the injected [Navigator], keeping navigation off host-provided lambdas.
 */
@HiltViewModel
class FullscreenVideoViewModel @Inject constructor(
    private val navigator: Navigator,
) : ViewModel() {

    fun onEvent(event: FullscreenVideoUiEvent): Unit = when (event) {
        FullscreenVideoUiEvent.GoBack -> {
            navigator.goBack()
        }
    }
}
