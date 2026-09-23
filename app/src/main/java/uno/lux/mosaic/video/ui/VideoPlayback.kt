package uno.lux.mosaic.video.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import uno.lux.mosaic.common.util.findActivity
import javax.inject.Inject

@Stable
class VideoPlaybackController(
    private val appContext: Context,
) {

    /**
     * [player] outlives any one clip, so this, not `player != null`, says whether a video is
     * showing.
     */
    var activeVideoUrl by mutableStateOf<String?>(null)
        private set

    var isFullscreen by mutableStateOf(false)
        private set

    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    private var ownedByInline = false

    fun playInline(url: String) {
        ensurePlayer(url)
        ownedByInline = true
    }

    fun enterFullscreen() {
        isFullscreen = true
    }

    fun openFullscreen(url: String) {
        if (activeVideoUrl != url) {
            ensurePlayer(url)
            ownedByInline = false
        }
        isFullscreen = true
    }

    fun exitFullscreen() {
        isFullscreen = false
        if (!ownedByInline) stopPlayback()
    }

    fun pause() {
        player?.pause()
    }

    fun stopPlayback() {
        player?.pause()
        activeVideoUrl = null
        ownedByInline = false
    }

    fun release() {
        player?.release()
        player = null
        activeVideoUrl = null
        ownedByInline = false
        isFullscreen = false
    }

    private fun ensurePlayer(url: String) {
        if (activeVideoUrl == url && player != null) return
        val exo = player ?: ExoPlayer.Builder(appContext).build().also { player = it }
        exo.apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = true
            prepare()
        }
        activeVideoUrl = url
    }
}

@HiltViewModel
class VideoPlaybackViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    val controller = VideoPlaybackController(context)

    override fun onCleared() {
        controller.release()
    }
}

/** Null outside the app shell, such as in previews. */
val LocalVideoPlayback = compositionLocalOf<VideoPlaybackController?> { null }

@Composable
fun ProvideVideoPlayback(content: @Composable () -> Unit) {
    val activity = LocalContext.current.findActivity() as ComponentActivity
    val playback = hiltViewModel<VideoPlaybackViewModel>(activity).controller
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playback.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalVideoPlayback provides playback, content = content)
}
