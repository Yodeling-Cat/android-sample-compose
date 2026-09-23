package uno.lux.mosaic.home.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MostVisibleVideoTest {

    private data class Item(
        override val index: Int,
        override val offset: Int,
        override val size: Int,
    ) : LazyListItemInfo {
        override val key: Any = index
    }

    private fun mostVisible(vararg items: Item, videos: Set<Int>): Int? =
        mostVisibleVideoIndex(
            visibleItems = items.toList(),
            viewportStart = 0,
            viewportEnd = 1000,
            hasVideo = { it in videos },
        )

    @Test
    fun `the video showing the most of itself wins`() {
        val index = mostVisible(
            Item(index = 0, offset = -300, size = 600),
            Item(index = 1, offset = 300, size = 600),
            videos = setOf(0, 1),
        )

        assertEquals(1, index)
    }

    @Test
    fun `an item with no video never wins, however visible`() {
        val index = mostVisible(
            Item(index = 0, offset = 0, size = 500),
            Item(index = 1, offset = 500, size = 800),
            videos = setOf(1),
        )

        assertEquals(1, index)
    }

    @Test
    fun `a winner showing less than half of itself plays nothing`() {
        val index = mostVisible(
            Item(index = 0, offset = 0, size = 800),
            Item(index = 1, offset = 800, size = 600),
            videos = setOf(1),
        )

        assertNull(index)
    }

    @Test
    fun `exactly half visible is enough`() {
        val index = mostVisible(
            Item(index = 0, offset = 700, size = 600),
            videos = setOf(0),
        )

        assertEquals(0, index)
    }

    @Test
    fun `on a tie the upper item wins`() {
        val index = mostVisible(
            Item(index = 0, offset = 0, size = 400),
            Item(index = 1, offset = 400, size = 400),
            videos = setOf(0, 1),
        )

        assertEquals(0, index)
    }

    @Test
    fun `no video on screen plays nothing`() {
        val index = mostVisible(
            Item(index = 0, offset = 0, size = 500),
            videos = emptySet(),
        )

        assertNull(index)
    }

    @Test
    fun `an item with no height is skipped rather than divided by`() {
        val index = mostVisible(
            Item(index = 0, offset = 0, size = 0),
            Item(index = 1, offset = 0, size = 600),
            videos = setOf(0, 1),
        )

        assertEquals(1, index)
    }
}
