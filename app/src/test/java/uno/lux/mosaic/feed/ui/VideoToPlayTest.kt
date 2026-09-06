package uno.lux.mosaic.feed.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The feed's playback rule, exercised without a composition: [videoToPlay] is the whole of what
 * the auto-play setting changes about the feed.
 */
class VideoToPlayTest {

    private val onScreen = "https://example.test/on-screen.mp4"
    private val other = "https://example.test/other.mp4"

    @Test
    fun `auto-play plays the on-screen winner`() {
        val url = videoToPlay(
            mostVisibleUrl = onScreen,
            activeUrl = null,
            autoPlayVideos = true,
        )

        assertEquals(onScreen, url)
    }

    @Test
    fun `auto-play stops once no video qualifies`() {
        val url = videoToPlay(
            mostVisibleUrl = null,
            activeUrl = onScreen,
            autoPlayVideos = true,
        )

        assertNull(url)
    }

    @Test
    fun `auto-play switches to the video that scrolled into view`() {
        val url = videoToPlay(
            mostVisibleUrl = onScreen,
            activeUrl = other,
            autoPlayVideos = true,
        )

        assertEquals(onScreen, url)
    }

    @Test
    fun `without auto-play nothing starts on its own`() {
        val url = videoToPlay(
            mostVisibleUrl = onScreen,
            activeUrl = null,
            autoPlayVideos = false,
        )

        assertNull(url)
    }

    // A tapped video is the one case that plays with the setting off, so that turning auto-play
    // off never means "videos can't be watched".
    @Test
    fun `without auto-play a video the user started keeps playing while it is the winner`() {
        val url = videoToPlay(
            mostVisibleUrl = onScreen,
            activeUrl = onScreen,
            autoPlayVideos = false,
        )

        assertEquals(onScreen, url)
    }

    @Test
    fun `without auto-play a tapped video stops when it scrolls out of the way`() {
        val url = videoToPlay(
            mostVisibleUrl = other,
            activeUrl = onScreen,
            autoPlayVideos = false,
        )

        assertNull(url)
    }

    @Test
    fun `without auto-play a tapped video stops once no video qualifies`() {
        val url = videoToPlay(
            mostVisibleUrl = null,
            activeUrl = onScreen,
            autoPlayVideos = false,
        )

        assertNull(url)
    }
}
