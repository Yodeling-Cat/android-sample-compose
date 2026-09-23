package uno.lux.mosaic.album.ui

sealed interface AlbumViewerUiEvent {

    data object GoBack : AlbumViewerUiEvent
}
