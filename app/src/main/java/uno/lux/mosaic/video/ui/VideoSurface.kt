package uno.lux.mosaic.video.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

/**
 * The one place a `PlayerView` is built. Both surfaces that show video — the inline post and the
 * full-screen page — attach the *same* player to one of these, so the chrome they draw has to
 * match; keeping a single factory is what stops the two drifting apart.
 *
 * It owns no playback. [player] is whatever the caller decides this surface should be showing at
 * this moment, and null detaches without releasing, which is how the inline post lets go while
 * full screen has the stream. Everything that varies between the two is a parameter, and there is
 * deliberately nothing else: no [LocalVideoPlayback] lookup here, because which player a surface
 * gets is the caller's decision to explain.
 *
 * [isFullscreen] tells the control which way it points — media3 swaps the glyph and its content
 * description to match — not where this surface is drawn. [onFullscreenClick] fires only for a
 * real tap, which media3 takes some care to keep separate; the comment on the binding says how.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoSurface(
    player: Player?,
    title: String?,
    isFullscreen: Boolean,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The factory runs once, so a listener built there would keep calling the first lambda it
    // captured. This is what keeps it pointing at the current one.
    val currentOnFullscreenClick by rememberUpdatedState(onFullscreenClick)

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setBackgroundColor(android.graphics.Color.BLACK)
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowRewindButton(false)
                setShowFastForwardButton(false)
            }
        },
        // Binding rather than construction, so a surface that outlives the value it was given
        // follows it.
        update = { view ->
            view.contentDescription = title

            // setFullscreenButtonState does not just set the icon: it reports the change to the
            // same listener a tap on the control goes through, so pushing our own state down with
            // the listener attached reads as the user having tapped it. On the full-screen page
            // that meant an instant pop. Detaching first is what tells the two apart. Passing null
            // does not hide the button — PlayerView keeps its own listener registered either way —
            // and setFullscreenButtonState is a no-op once the state already matches, so the
            // round trip costs nothing after the first update.
            view.setFullscreenButtonClickListener(null)
            view.setFullscreenButtonState(isFullscreen)
            view.setFullscreenButtonClickListener { currentOnFullscreenClick() }

            view.player = player
        },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}
