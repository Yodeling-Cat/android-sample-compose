package uno.lux.mosaic.shell.ui

import uno.lux.mosaic.app.navigation.Screen

sealed interface ShellUiEvent {

    data class OpenDestination(
        val screen: Screen,
    ) : ShellUiEvent
}
