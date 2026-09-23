package uno.lux.mosaic.shell.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import uno.lux.mosaic.app.navigation.Navigator
import uno.lux.mosaic.app.navigation.Screen
import javax.inject.Inject

/**
 * Backs the tabbed shell itself. The shell holds no content state — which tab is selected is
 * plain composition state, since switching tabs is not a navigation event — so its only intent is
 * the one navigation-suite item that *does* navigate: a [ShellDestinations] entry carrying a
 * [Screen] opens that page over the whole shell instead of swapping the content area.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val navigator: Navigator,
) : ViewModel() {

    fun onEvent(event: ShellUiEvent): Unit = when (event) {
        is ShellUiEvent.OpenDestination -> {
            navigator.goToSingleTop(event.screen)
        }
    }
}
