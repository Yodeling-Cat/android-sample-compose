package uno.lux.mosaic.video.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import uno.lux.mosaic.app.navigation.Navigator
import javax.inject.Inject
import uno.lux.mosaic.video.ui.FullscreenVideoUiEvent as UiEvent

@HiltViewModel
class FullscreenVideoViewModel @Inject constructor(
    private val navigator: Navigator,
) : ViewModel() {

    fun onEvent(event: UiEvent): Unit = when (event) {
        UiEvent.GoBack -> {
            navigator.goBack()
        }
    }
}
