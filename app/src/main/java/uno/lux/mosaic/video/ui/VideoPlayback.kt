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
 * Owns the app's single video [ExoPlayer]. The instance is built once and **reused** for every
 * video: moving between clips is a [setMediaItem][ExoPlayer.setMediaItem] + [prepare][ExoPlayer.prepare]
 * on the same player, never a rebuild. That keeps the renderer threads and codec pipeline warm, so
 * a new clip starts fast instead of paying to allocate a fresh player — which is what a feed scroll
 * past a run of videos would otherwise cost on every item. It is torn down only in [release], when
 * playback is done for good. The same reuse lets one playback move between an inline post and the
 * full-screen page without being recreated — the position lives in the player, so keeping the
 * instance is what preserves it.
 *
 * Exactly one video plays at a time. [activeVideoUrl], [isFullscreen] and [player] are Compose
 * state, so the inline player and the full-screen page recompose as playback moves between them.
 * The player instance outlives any single clip, so [activeVideoUrl] — not `player != null` — is
 * what says whether something is on screen: [stopPlayback] clears it (reverting inline views to
 * their thumbnail) while keeping the warm player around for the next clip. [ownedByInline] records
 * whether an inline post started the playback: if so, leaving full screen returns to that post and
 * keeps playing; if a profile thumbnail opened it straight into full screen, leaving stops it. The
 * URL is the player's identity — every uploaded video has its own, and a clip picked in the composer
 * brings a content URI — so one keying serves posts and local previews alike, with no id a local
 * file wouldn't have.
 *
 * The player is an Android component with no logic to unit test, so this is a plain holder built
 * on [Context] rather than a constructor-injected, JVM-testable unit.
 */
@Stable
class VideoPlaybackController(
    private val appContext: Context,
) {

    /** The video currently loaded into [player], or null when nothing is playing. */
    var activeVideoUrl by mutableStateOf<String?>(null)
        private set

    /** True while playback is showing on the full-screen page rather than inline. */
    var isFullscreen by mutableStateOf(false)
        private set

    /** The shared player, or null when nothing is loaded. Attach it to a `PlayerView`. */
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

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
     * Stops the current video but keeps the player instance warm for the next one. Clearing
     * [activeVideoUrl] reverts inline posts to their thumbnail; the player itself is only ever
     * released in [release]. This is the feed's "nothing on screen should play" path, so a scroll
     * from one video to the next reuses the same player instead of rebuilding it.
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
 * Puts the shared player in reach of [content]. The controller is held by an **activity-scoped**
 * [VideoPlaybackViewModel], so it survives the push to full screen and configuration changes and is
 * released when the activity finishes; providing it around the whole back stack is what lets an
 * inline post and the full-screen page reach the same instance.
 *
 * Playback also pauses whenever the app is backgrounded — it resumes on the user's next tap — which
 * belongs here rather than in a screen: the player outlives every one of them.
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
