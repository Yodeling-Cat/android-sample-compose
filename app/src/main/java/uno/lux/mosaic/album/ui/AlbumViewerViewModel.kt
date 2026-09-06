package uno.lux.mosaic.album.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import uno.lux.mosaic.app.navigation.Navigator
import javax.inject.Inject

/**
 * The album viewer holds no screen state of its own — the images are carried by its back-stack
 * key and the pager state lives in the composable — so its only responsibility is routing the
 * back affordance through the injected [Navigator], keeping navigation off host-provided
 * lambdas like every other screen.
 */
@HiltViewModel
class AlbumViewerViewModel @Inject constructor(
    private val navigator: Navigator,
) : ViewModel() {

    fun goBack() = navigator.goBack()
}
