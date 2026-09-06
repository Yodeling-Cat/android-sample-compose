package uno.lux.mosaic.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RelativeTimeTest {

    private val now: Instant = Instant.parse("2026-06-02T12:00:00Z")

    @Test
    fun `relative time under a minute is Now`() {
        assertEquals(RelativeTime.Now, relativeTime(now.minus(Duration.ofSeconds(30)), now))
    }

    @Test
    fun `relative time rounds down to whole minutes`() {
        assertEquals(
            RelativeTime.Minutes(5),
            relativeTime(now.minus(Duration.ofSeconds(5 * 60 + 59)), now),
        )
    }

    @Test
    fun `relative time in hours`() {
        assertEquals(RelativeTime.Hours(3), relativeTime(now.minus(Duration.ofHours(3)), now))
    }

    @Test
    fun `relative time in days`() {
        assertEquals(RelativeTime.Days(2), relativeTime(now.minus(Duration.ofDays(2)), now))
    }

    @Test
    fun `relative time switches to weeks at seven days`() {
        assertEquals(RelativeTime.Weeks(2), relativeTime(now.minus(Duration.ofDays(14)), now))
    }
}
