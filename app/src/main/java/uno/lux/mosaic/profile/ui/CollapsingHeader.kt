package uno.lux.mosaic.profile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/*
 * A collapsing header: the header slides up behind the app bar as the list scrolls, and whatever
 * sits below it rides up too, until the header's foot is flush beneath the bar. Nested scrolling
 * advances one `collapse` offset, which the header reads in its layout pass and the bar reads to
 * fade itself in.
 */

@Stable
internal class CollapsingHeaderState(
    initialCollapse: Float = 0f,
) {
    private val _collapse = mutableFloatStateOf(initialCollapse)
    val collapse: FloatState get() = _collapse

    var maxCollapse by mutableFloatStateOf(0f)
        private set

    val collapsedFraction: Float
        get() = if (maxCollapse > 0f) (_collapse.floatValue / maxCollapse).coerceIn(0f, 1f) else 0f

    fun onHeaderMeasured(heightPx: Int, pinnedAtPx: Int) {
        val max = (heightPx - pinnedAtPx).coerceAtLeast(0).toFloat()
        if (max != maxCollapse) maxCollapse = max
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        // Scrolling up collapses the header before the list scrolls.
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y >= 0f) Offset.Zero else consume(available.y)

        // Scrolling down re-expands the header from the leftover once the list is at its top — and,
        // when pull-to-refresh wraps this, before the refresh sees it.
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset =
            if (available.y <= 0f) Offset.Zero else consume(available.y)
    }

    // Returns what it consumed, with the same sign as `dy`.
    private fun consume(dy: Float): Offset {
        val before = _collapse.floatValue
        _collapse.floatValue = (before - dy).coerceIn(0f, maxCollapse)

        return Offset(x = 0f, y = before - _collapse.floatValue)
    }

    companion object {
        val Saver: Saver<CollapsingHeaderState, Float> = Saver(
            save = { it._collapse.floatValue },
            restore = { CollapsingHeaderState(it) },
        )
    }
}

/**
 * Saveable, unlike the measured height: the collapse *is* the top of the page's scroll position,
 * and `rememberLazyListState` only remembers the part below it.
 */
@Composable
internal fun rememberCollapsingHeaderState(): CollapsingHeaderState =
    rememberSaveable(saver = CollapsingHeaderState.Saver) { CollapsingHeaderState() }

/**
 * Reserves `collapse` less height and draws the header shifted up by that much, so it slides
 * behind the bar with no gap below. Read in the layout pass, so a scroll reflows the header without
 * recomposing anything.
 */
internal fun Modifier.collapsingHeader(state: CollapsingHeaderState, pinnedAtPx: Int): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        state.onHeaderMeasured(heightPx = placeable.height, pinnedAtPx = pinnedAtPx)

        val offset = state.collapse.floatValue.roundToInt()
        layout(placeable.width, (placeable.height - offset).coerceAtLeast(0)) {
            placeable.place(0, -offset)
        }
    }
