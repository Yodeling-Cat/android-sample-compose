package uno.lux.mosaic.video.ui

sealed interface FullscreenVideoUiEvent {

    data object GoBack : FullscreenVideoUiEvent
}
