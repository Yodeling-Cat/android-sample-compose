package uno.lux.mosaic.album.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import uno.lux.mosaic.app.navigation.Navigator
import javax.inject.Inject

@HiltViewModel
class AlbumViewerViewModel @Inject constructor(
    private val navigator: Navigator,
) : ViewModel() {

    fun onEvent(event: AlbumViewerUiEvent): Unit = when (event) {
        AlbumViewerUiEvent.GoBack -> {
            navigator.goBack()
        }
    }
}
