package uno.lux.mosaic.profile.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CollapsingHeaderStateTest {

    private fun measured(collapse: Float = 0f) = CollapsingHeaderState(collapse).apply {
        onHeaderMeasured(heightPx = 500, pinnedAtPx = 100)
    }

    private fun CollapsingHeaderState.preScroll(dy: Float): Offset =
        nestedScrollConnection.onPreScroll(Offset(x = 0f, y = dy), NestedScrollSource.UserInput)

    private fun CollapsingHeaderState.postScroll(dy: Float): Offset =
        nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(x = 0f, y = dy),
            source = NestedScrollSource.UserInput,
        )

    @Test
    fun `the header collapses until its foot reaches the bar`() {
        val state = measured()

        assertEquals(400f, state.maxCollapse)
    }

    @Test
    fun `a header shorter than the bar never collapses`() {
        val state = CollapsingHeaderState()

        state.onHeaderMeasured(heightPx = 80, pinnedAtPx = 100)

        assertEquals(0f, state.maxCollapse)
    }

    @Test
    fun `scrolling up collapses the header before the list scrolls`() {
        val state = measured()

        val consumed = state.preScroll(-150f)

        assertEquals(Offset(x = 0f, y = -150f), consumed)
        assertEquals(150f, state.collapse.floatValue)
    }

    @Test
    fun `scrolling up past full collapse leaves the rest to the list`() {
        val state = measured(collapse = 350f)

        val consumed = state.preScroll(-150f)

        assertEquals(Offset(x = 0f, y = -50f), consumed)
        assertEquals(400f, state.collapse.floatValue)
    }

    @Test
    fun `scrolling down goes to the list first`() {
        val state = measured(collapse = 200f)

        val consumed = state.preScroll(150f)

        assertEquals(Offset.Zero, consumed)
        assertEquals(200f, state.collapse.floatValue)
    }

    @Test
    fun `the leftover of a downward scroll re-expands the header`() {
        val state = measured(collapse = 100f)

        val consumed = state.postScroll(150f)

        assertEquals(Offset(x = 0f, y = 100f), consumed)
        assertEquals(0f, state.collapse.floatValue)
    }

    @Test
    fun `the leftover of an upward scroll is not the header's`() {
        val state = measured(collapse = 100f)

        val consumed = state.postScroll(-150f)

        assertEquals(Offset.Zero, consumed)
        assertEquals(100f, state.collapse.floatValue)
    }

    @Test
    fun `the collapsed fraction runs from zero to one`() {
        val state = measured(collapse = 100f)

        assertEquals(0.25f, state.collapsedFraction)
        state.preScroll(-1000f)
        assertEquals(1f, state.collapsedFraction)
    }

    @Test
    fun `an unmeasured header reads as expanded`() {
        val state = CollapsingHeaderState(initialCollapse = 100f)

        assertEquals(0f, state.collapsedFraction)
    }
}
