package uno.lux.mosaic.video.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchActiveVideoTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val viewers = VideoViewers()
    private var unwatched = 0

    @Test
    fun aPageSwappedForAnotherWatcherInOneFrameKeepsPlaying() {
        var page by mutableIntStateOf(0)
        setContent { key(page) { WatchActiveVideo(viewers) } }

        composeRule.runOnIdle { page = 1 }
        composeRule.waitForIdle()

        assertEquals(0, unwatched)
    }

    @Test
    fun theLastWatcherLeavingPauses() {
        var watching by mutableStateOf(true)
        setContent { if (watching) WatchActiveVideo(viewers) }

        composeRule.runOnIdle { watching = false }
        composeRule.waitForIdle()

        assertEquals(1, unwatched)
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            LaunchedEffect(viewers) { viewers.onEachUnwatched { unwatched++ } }
            content()
        }
    }
}
