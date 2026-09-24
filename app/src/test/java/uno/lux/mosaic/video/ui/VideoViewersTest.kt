package uno.lux.mosaic.video.ui

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewersTest {

    private val viewers = VideoViewers()
    private var unwatched = 0

    @Test
    fun `the last viewer leaving reports the video unwatched`() = runTest {
        collect()

        settle { viewers.attach() }
        settle { viewers.detach() }

        assertEquals(1, unwatched)
    }

    @Test
    fun `nothing is reported before anyone has watched`() = runTest {
        collect()

        assertEquals(0, unwatched)
    }

    @Test
    fun `a viewer leaving while another stays reports nothing`() = runTest {
        collect()

        settle {
            viewers.attach()
            viewers.attach()
        }
        settle { viewers.detach() }

        assertEquals(0, unwatched)
    }

    @Test
    fun `a hand-over within one apply reports nothing`() = runTest {
        collect()

        settle { viewers.attach() }
        settle {
            viewers.detach()
            viewers.attach()
        }

        assertEquals(0, unwatched)
    }

    @Test
    fun `every time the video goes unwatched is reported`() = runTest {
        collect()

        settle { viewers.attach() }
        settle { viewers.detach() }
        settle { viewers.attach() }
        settle { viewers.detach() }

        assertEquals(2, unwatched)
    }

    private fun TestScope.collect() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewers.onEachUnwatched { unwatched++ }
        }
    }

    /** Writes [block] to global state and delivers it the way a composition's apply would. */
    private fun TestScope.settle(block: () -> Unit) {
        block()
        Snapshot.sendApplyNotifications()
        runCurrent()
    }
}
