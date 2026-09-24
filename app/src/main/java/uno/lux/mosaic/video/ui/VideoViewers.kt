package uno.lux.mosaic.video.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/** Counts the surfaces on screen that show the active video. */
@Stable
class VideoViewers {
    private var count by mutableIntStateOf(0)

    fun attach() {
        count++
    }

    fun detach() {
        count--
    }

    /**
     * Suspends until cancelled, running [action] each time the last viewer leaves. A surface that
     * leaves in the same apply another one arrives in is a hand-over, not a leave: a page swapped
     * for another that shows the same video must not pause it.
     */
    suspend fun onEachUnwatched(action: () -> Unit) {
        snapshotFlow { count > 0 }
            // The first value is where collection started, not a viewer leaving.
            .drop(1)
            .filter { watched -> !watched }
            .collect { action() }
    }
}

/** Counts the caller as a viewer of the active video for as long as it stays composed. */
@Composable
internal fun WatchActiveVideo(viewers: VideoViewers) {
    DisposableEffect(viewers) {
        viewers.attach()
        onDispose { viewers.detach() }
    }
}
