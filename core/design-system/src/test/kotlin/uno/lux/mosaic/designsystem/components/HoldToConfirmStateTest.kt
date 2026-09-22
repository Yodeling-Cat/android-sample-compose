package uno.lux.mosaic.designsystem.components

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val HOLD_MILLIS = 1_500L
private const val HINT_MILLIS = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class HoldToConfirmStateTest {

    @Test
    fun `holding for the whole duration confirms once`() = runTest {
        var confirmed = 0
        val state = HoldToConfirmState(HOLD_MILLIS, HINT_MILLIS)

        backgroundScope.launch {
            state.press(awaitRelease = { awaitCancellation() }) { confirmed++ }
        }
        runCurrent()
        assertEquals(HoldPhase.HOLDING, state.phase)

        advanceTimeBy(HOLD_MILLIS - 1)
        assertEquals(0, confirmed)
        assertEquals(HoldPhase.HOLDING, state.phase)

        advanceTimeBy(2)
        assertEquals(1, confirmed)
        assertEquals(HoldPhase.IDLE, state.phase)

        // The finger is still down; the action must not repeat while it stays there.
        advanceTimeBy(HOLD_MILLIS * 2)
        assertEquals(1, confirmed)
    }

    @Test
    fun `releasing early hints instead of confirming`() = runTest {
        var confirmed = 0
        val state = HoldToConfirmState(HOLD_MILLIS, HINT_MILLIS)

        backgroundScope.launch { state.press(awaitRelease = { delay(400) }) { confirmed++ } }
        runCurrent()

        advanceTimeBy(401)
        assertEquals(HoldPhase.HINT, state.phase)
        assertEquals(0, confirmed)
    }

    @Test
    fun `the hint clears itself after its duration`() = runTest {
        val state = HoldToConfirmState(HOLD_MILLIS, HINT_MILLIS)

        backgroundScope.launch { state.press(awaitRelease = { delay(400) }) {} }
        runCurrent()
        advanceTimeBy(401)

        advanceTimeBy(HINT_MILLIS - 1)
        assertEquals(HoldPhase.HINT, state.phase)

        advanceTimeBy(2)
        assertEquals(HoldPhase.IDLE, state.phase)
    }

    /**
     * The likely sequence: a user taps, reads the hint, and presses again straight away. The
     * expiring hint of the spent press must not drag the button out of the new hold.
     */
    @Test
    fun `a press started during the hint supersedes it`() = runTest {
        var confirmed = 0
        val state = HoldToConfirmState(HOLD_MILLIS, HINT_MILLIS)

        backgroundScope.launch { state.press(awaitRelease = { delay(200) }) { confirmed++ } }
        runCurrent()
        advanceTimeBy(201)
        assertEquals(HoldPhase.HINT, state.phase)

        backgroundScope.launch {
            state.press(awaitRelease = { awaitCancellation() }) { confirmed++ }
        }
        runCurrent()
        assertEquals(HoldPhase.HOLDING, state.phase)

        // A full hint's worth of time: whenever the first press's hint was due to expire, it has.
        // Still short of the second press's own hold, which started later.
        advanceTimeBy(HINT_MILLIS)
        assertEquals(HoldPhase.HOLDING, state.phase)
        assertEquals(0, confirmed)

        advanceTimeBy(HOLD_MILLIS)
        assertEquals(1, confirmed)
        assertEquals(HoldPhase.IDLE, state.phase)
    }

    @Test
    fun `a cancelled press leaves the button idle`() = runTest {
        var confirmed = 0
        val state = HoldToConfirmState(HOLD_MILLIS, HINT_MILLIS)

        val press = backgroundScope.launch {
            state.press(awaitRelease = { awaitCancellation() }) { confirmed++ }
        }
        runCurrent()
        assertEquals(HoldPhase.HOLDING, state.phase)

        press.cancel()
        runCurrent()
        assertEquals(HoldPhase.IDLE, state.phase)
        assertEquals(0, confirmed)
    }
}
