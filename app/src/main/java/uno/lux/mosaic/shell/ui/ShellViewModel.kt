package uno.lux.mosaic.shell.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import uno.lux.mosaic.app.navigation.Navigator
import javax.inject.Inject
import uno.lux.mosaic.shell.ui.ShellUiEvent as UiEvent

@HiltViewModel
class ShellViewModel @Inject constructor(
    private val navigator: Navigator,
) : ViewModel() {

    fun onEvent(event: UiEvent): Unit = when (event) {
        is UiEvent.OpenDestination -> {
            navigator.goToSingleTop(event.screen)
        }
    }
}
