package uno.lux.mosaic.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClickDebounceGuardTest {

    @Test
    fun `first click is always dispatched`() {
        var count = 0
        var time = 1_000L
        val guard = ClickDebounceGuard(500L) { time }

        guard.tryFire { count++ }

        assertEquals(1, count)
    }

    @Test
    fun `click within debounce window is ignored`() {
        var count = 0
        var time = 1_000L
        val guard = ClickDebounceGuard(500L) { time }

        guard.tryFire { count++ }
        time += 499L
        guard.tryFire { count++ }

        assertEquals(1, count)
    }

    @Test
    fun `click at exact debounce boundary is still ignored`() {
        var count = 0
        var time = 1_000L
        val guard = ClickDebounceGuard(500L) { time }

        guard.tryFire { count++ }
        time += 500L
        guard.tryFire { count++ }

        assertEquals(1, count)
    }

    @Test
    fun `click past debounce window is dispatched`() {
        var count = 0
        var time = 1_000L
        val guard = ClickDebounceGuard(500L) { time }

        guard.tryFire { count++ }
        time += 501L
        guard.tryFire { count++ }

        assertEquals(2, count)
    }

    @Test
    fun `rapid burst fires once then allows after window`() {
        var count = 0
        var time = 1_000L
        val guard = ClickDebounceGuard(500L) { time }

        repeat(10) {
            time += 10L
            guard.tryFire { count++ }
        }
        assertEquals(1, count)

        time += 501L
        guard.tryFire { count++ }
        assertEquals(2, count)
    }
}
