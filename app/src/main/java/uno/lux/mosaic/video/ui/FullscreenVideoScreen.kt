package uno.lux.mosaic.video.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import uno.lux.mosaic.app.util.ImmersiveSystemBars
import uno.lux.mosaic.app.util.findActivity
import uno.lux.mosaic.common.ui.OverlayBackButton

/**
 * Stateful entry point: binds the [FullscreenVideoViewModel] (its sole job is routing back
 * through the `Navigator`) and forwards to the stateless overload below.
 */
@Composable
fun FullscreenVideoScreen(
    url: String,
    title: String?,
    modifier: Modifier = Modifier,
    viewModel: FullscreenVideoViewModel = hiltViewModel(),
) {
    FullscreenVideoScreen(
        url = url,
        title = title,
        onBack = viewModel::goBack,
        modifier = modifier,
    )
}

/**
 * The full-screen video page, pushed over the rest of the app. It does not own a player: it
 * reuses the shared one from [LocalVideoPlayback], so a video promoted from an inline post keeps
 * playing from the same position. [openFullscreen] either adopts the running inline player or, if
 * this page is the entry point (a profile thumbnail), loads the video fresh; the matching teardown
 * runs in [exitFullscreen] when the page is really popped. The system bars hide for an immersive
 * stage and are restored on the way out. Holding no ViewModel makes it directly previewable.
 */
@Composable
internal fun FullscreenVideoScreen(
    url: String,
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playback = LocalVideoPlayback.current

    LaunchedEffect(playback, url) {
        playback?.openFullscreen(url)
    }

    // Exit only on a real pop, not a configuration change — a rotation must keep playing.
    val activity = LocalContext.current.findActivity()

    DisposableEffect(playback) {
        onDispose {
            if (activity?.isChangingConfigurations != true) playback?.exitFullscreen()
        }
    }

    ImmersiveSystemBars()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VideoSurface(
            player = playback?.player,
            title = title,
            // The control already reads as "exit full screen"; tapping it pops the page.
            isFullscreen = true,
            onFullscreenClick = onBack,
            modifier = Modifier.fillMaxSize(),
        )

        OverlayBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
    }
}
