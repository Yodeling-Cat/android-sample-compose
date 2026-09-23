package uno.lux.mosaic.shell.ui

import uno.lux.mosaic.app.navigation.Screen

sealed interface ShellUiEvent {

    /** A [ShellDestinations] entry that carries a [screen] was selected, so it opens that page. */
    data class OpenDestination(
        val screen: Screen,
    ) : ShellUiEvent
}
