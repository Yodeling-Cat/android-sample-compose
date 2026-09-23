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

/**
 * Owns the app's single [ExoPlayer], reused for every clip so the codec pipeline stays warm and a
 * playback can move between an inline post and the full-screen page without losing its position.
 * Only [release] tears it down.
 *
 * A video is keyed by its URL, so posts and composer previews (content URIs) are handled alike.
 *
 * Built on [Context] rather than as a JVM-testable unit, because the player has no logic to test.
 */
@Stable
class VideoPlaybackController(
    private val appContext: Context,
) {

    /**
     * The video on screen, or null when nothing is. [player] outlives any one clip, so this, not
     * `player != null`, is what says whether a video is showing.
     */
    var activeVideoUrl by mutableStateOf<String?>(null)
        private set

    /** True while playback is showing on the full-screen page rather than inline. */
    var isFullscreen by mutableStateOf(false)
        private set

    /** The shared player, or null when nothing is loaded. Attach it to a `PlayerView`. */
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    /**
     * Whether an inline post started this playback. If one did, leaving full screen returns to it
     * still playing; if a profile thumbnail opened the video straight into full screen, leaving
     * stops it.
     */
    private var ownedByInline = false

    /** Starts (or resumes) inline playback of [url], the feed/profile-post entry point. */
    fun playInline(url: String) {
        ensurePlayer(url)
        ownedByInline = true
    }

    /** Promotes the already-playing inline video to full screen. */
    fun enterFullscreen() {
        isFullscreen = true
    }

    /**
     * Shows [url] on the full-screen page. If it is already the active video (an inline post
     * promoted it) the running player is reused untouched; otherwise it is loaded fresh with no
     * inline owner, so [exitFullscreen] will release it.
     */
    fun openFullscreen(url: String) {
        if (activeVideoUrl != url) {
            ensurePlayer(url)
            ownedByInline = false
        }
        isFullscreen = true
    }

    /** Leaves the full-screen page, stopping playback unless an inline post still owns it. */
    fun exitFullscreen() {
        isFullscreen = false
        if (!ownedByInline) stopPlayback()
    }

    /** Pauses playback without tearing the player down (e.g. when the app is backgrounded). */
    fun pause() {
        player?.pause()
    }

    /**
     * Stops the current video but keeps the player warm for the next one. Clearing
     * [activeVideoUrl] reverts inline posts to their thumbnail.
     */
    fun stopPlayback() {
        player?.pause()
        activeVideoUrl = null
        ownedByInline = false
    }

    /** Releases the player and clears all playback state; call when playback is done for good. */
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

/**
 * Holds the [VideoPlaybackController] at activity scope so the shared player survives both the
 * navigation push to full screen and configuration changes, and is released in [onCleared] when
 * the activity is finished for good. Built on the application context, so it leaks nothing.
 */
@HiltViewModel
class VideoPlaybackViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    val controller = VideoPlaybackController(context)

    override fun onCleared() {
        controller.release()
    }
}

/**
 * The shared [VideoPlaybackController] for the current composition. Null outside the app shell
 * (e.g. in `@Preview`s), where video controls render their static thumbnail state.
 */
val LocalVideoPlayback = compositionLocalOf<VideoPlaybackController?> { null }

/**
 * Provides the shared player to [content]. Wrapping the whole back stack lets an inline post and
 * the full-screen page reach the same instance, which an activity-scoped [VideoPlaybackViewModel]
 * keeps across configuration changes.
 *
 * Pauses playback whenever the app is backgrounded. That lives here because the player outlives
 * every screen.
 */
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
